package com.buzzkill.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.buzzkill.data.RuleRepository
import com.buzzkill.data.SettingsStore
import com.buzzkill.data.model.Rule
import com.buzzkill.engine.Decision
import com.buzzkill.engine.MatchContext
import com.buzzkill.engine.RuleEngine
import com.buzzkill.engine.SideEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The live entry point: receives every posted notification, runs it through the
 * [RuleEngine], and applies the resulting [Decision] (discard / repost / dismiss /
 * snooze / side effects).
 *
 * Active rules and the master switch are mirrored into memory so the hot path never
 * touches the database.
 */
class BuzzKillListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RuleEngine()

    private lateinit var repository: RuleRepository
    private lateinit var logRepository: com.buzzkill.data.NotificationLogRepository
    private lateinit var settings: SettingsStore
    private lateinit var channels: ChannelManager
    private lateinit var modifier: NotificationModifier
    private lateinit var tts: TtsManager
    private lateinit var sideEffects: SideEffectExecutor

    @Volatile private var activeRules: List<Rule> = emptyList()
    @Volatile private var masterEnabled: Boolean = true
    @Volatile private var logActivity: Boolean = true
    @Volatile private var connected: Boolean = false

    override fun onCreate() {
        super.onCreate()
        repository = RuleRepository.get(this)
        logRepository = com.buzzkill.data.NotificationLogRepository.get(this)
        settings = SettingsStore.get(this)
        channels = ChannelManager(this)
        modifier = NotificationModifier(this, channels)
        tts = TtsManager(this)
        sideEffects = SideEffectExecutor(this, scope, tts)
        channels.ensureBaseChannels()

        scope.launch {
            repository.observeAll().collectLatest {
                activeRules = it.filter(Rule::enabled)
                Log.i(TAG, "rules loaded: ${activeRules.size} enabled of ${it.size}")
            }
        }
        scope.launch { settings.masterEnabled.collectLatest { masterEnabled = it } }
        scope.launch { settings.logActivity.collectLatest { logActivity = it } }
        Log.i(TAG, "service onCreate")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        channels.ensureBaseChannels()
        instance = this
        Log.i(TAG, "listener connected; activeRules=${activeRules.size}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.i(TAG, "posted from ${sbn.packageName}; master=$masterEnabled rules=${activeRules.size}")
        if (!masterEnabled) return
        // Never process our own reposted copies — that would loop forever.
        if (sbn.packageName == packageName) return

        scope.launch {
            try {
                process(sbn)
            } catch (t: Throwable) {
                Log.e(TAG, "process failed for ${sbn.packageName}", t)
            }
        }
    }

    private suspend fun process(sbn: StatusBarNotification) {
        val device = DeviceState.sample(this)
        val appName = NotificationFields.appLabel(this, sbn.packageName)
        val ctx = MatchContext(
            packageName = sbn.packageName,
            appName = appName,
            fields = NotificationFields.extract(sbn),
            isOngoing = sbn.isOngoing,
            hasReply = NotificationFields.hasReplyAction(sbn),
            device = device,
        )

        val decision = engine.evaluate(ctx, activeRules)
        if (decision.matched) {
            applyDecision(sbn, decision, appName)
            recordFires(decision)
        }
        if (logActivity) logNotification(sbn, appName, decision)
    }

    private suspend fun logNotification(sbn: StatusBarNotification, appName: String, decision: Decision) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val outcome = when {
            decision.discard -> com.buzzkill.data.model.NotificationLog.OUTCOME_DISCARDED
            decision.needsRepost -> com.buzzkill.data.model.NotificationLog.OUTCOME_MODIFIED
            decision.snoozeMinutes != null -> com.buzzkill.data.model.NotificationLog.OUTCOME_SNOOZED
            decision.dismiss -> com.buzzkill.data.model.NotificationLog.OUTCOME_DISMISSED
            else -> com.buzzkill.data.model.NotificationLog.OUTCOME_NONE
        }
        runCatching {
            logRepository.add(
                com.buzzkill.data.model.NotificationLog(
                    time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    matched = decision.matched,
                    firedRuleIds = decision.firedRuleIds.joinToString(","),
                    outcome = outcome,
                )
            )
        }
    }

    private fun applyDecision(sbn: StatusBarNotification, decision: Decision, appName: String) {
        // Auto-reply needs the original notification's RemoteInput.
        decision.sideEffects.filterIsInstance<SideEffect.AutoReply>().forEach {
            AutoReplyHelper.reply(this, sbn, it.message)
        }
        sideEffects.execute(decision.sideEffects)

        // Danmaku replaces the native notification — but only suppress the native one
        // if the danmaku can actually be shown (overlay permission granted), otherwise
        // the notification would silently vanish.
        if (decision.sideEffects.any { it is SideEffect.Danmaku } && DanmakuController.canShow(this)) {
            safeCancel(sbn.key)
        }

        when {
            decision.discard -> safeCancel(sbn.key)
            decision.needsRepost -> {
                modifier.repost(sbn, decision, appName)
                // Remove the source notification; our rebuilt copy stands in its place.
                safeCancel(sbn.key)
            }
        }

        decision.snoozeMinutes?.let { minutes ->
            runCatching { snoozeNotification(sbn.key, minutes * 60_000L) }
        }

        if (decision.dismiss) {
            if (decision.dismissDelayMs > 0) {
                scope.launch {
                    delay(decision.dismissDelayMs)
                    safeCancel(sbn.key)
                }
            } else {
                safeCancel(sbn.key)
            }
        }
    }

    private fun safeCancel(key: String) {
        runCatching { cancelNotification(key) }
    }

    private fun recordFires(decision: Decision) {
        if (decision.firedRuleIds.isEmpty()) return
        scope.launch {
            decision.firedRuleIds.forEach { repository.incrementFireCount(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "BuzzKill"

        /** Non-null while the listener is connected; used by the UI to show status. */
        @Volatile
        var instance: BuzzKillListenerService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
