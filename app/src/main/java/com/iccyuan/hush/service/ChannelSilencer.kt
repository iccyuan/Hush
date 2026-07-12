package com.iccyuan.hush.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import com.iccyuan.hush.data.SilencedChannelStore
import com.iccyuan.hush.util.Logger

/**
 * 渠道级静音：直接把目标应用**自己的**通知渠道改成不发声不振动。
 *
 * 这是唯一能做到「完全静音 + 完整保留原通知」的途径。监听器路线（短暂 snooze 掐断提醒）
 * 依赖「系统把 snooze 的通知静默放回」这一约定，而部分 ROM（实测 ColorOS 16 / Android 16）
 * 会在放回时**重新播放完整提醒**，令其彻底失效；重发副本又会让通知脱离原应用。改渠道则是在
 * 系统层生效——通知照常由原应用发出、照常进通知栏和横幅，系统只是不再为它发声/振动。
 *
 * 依据 [NotificationListenerService.updateNotificationChannel] 的约定：调用方必须持有一个
 * CompanionDeviceManager 关联设备（或身为通知助手）。因此本能力需要用户先完成一次配套设备
 * 关联（见 [isAvailable]）；未关联时静音回退到监听器的就地静音路线。
 *
 * 只改「声音 + 振动」，不动重要性——横幅、锁屏、角标等行为保持原样，这正是「静音」的语义。
 * 原始设置在静音前快照到 [SilencedChannelStore]，解除静音时逐字段还原，避免把用户的应用
 * 永久改哑。
 */
object ChannelSilencer {

    // 本进程已确认改哑过的包，用于让 [ensureSilenced] 在热路径上零开销。进程重启后为空，
    // 届时第一条通知会重新走一次 [silence]——它对已静默的渠道是幂等的（不改、也不写快照）。
    private val silenced = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** 该能力当前是否可用：Android 8.0+ 且已关联配套设备。 */
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return hasCompanionAssociation(context)
    }

    /**
     * 确保 [pkg] 的渠道处于静音状态。用于「应用已在静音名单里」的常态路径——静音是长期状态，
     * 而渠道改写可能因进程重启、升级、当时未关联配套设备等原因没能落实，这里每次通知到达时
     * 补齐一次（命中内存缓存时直接返回，不触碰系统）。
     */
    fun ensureSilenced(listener: NotificationListenerService, pkg: String, userId: Int) {
        if (pkg in silenced) return
        if (!isAvailable(listener)) return
        silence(listener, pkg, userId)
    }

    fun hasCompanionAssociation(context: Context): Boolean = runCatching {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        cdm.associations.isNotEmpty()
    }.getOrDefault(false)

    /**
     * 静音 [pkg] 在用户空间 [userId] 下的全部通知渠道（关声音、关振动），并快照原始设置以便日后还原。
     * 返回是否至少改动了一个渠道。
     */
    fun silence(listener: NotificationListenerService, pkg: String, userId: Int): Boolean {
        if (!isAvailable(listener)) {
            Logger.w("channel-silence: unavailable (no companion association); pkg=$pkg")
            return false
        }
        val user = userHandle(userId)
        val channels = runCatching { listener.getNotificationChannels(pkg, user) }
            .onFailure { Logger.e("channel-silence: cannot read channels of $pkg", it) }
            .getOrNull()
            .orEmpty()
        if (channels.isEmpty()) {
            Logger.w("channel-silence: no channels for $pkg")
            return false
        }

        var changed = 0
        var allSilent = true
        for (channel in channels) {
            // 本就静默的渠道不用动，也不要为它写快照——否则解除静音时会“还原”出并不存在的声音。
            if (channel.sound == null && !channel.shouldVibrate()) continue
            SilencedChannelStore.remember(listener, pkg, userId, channel)
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.vibrationPattern = null
            runCatching { listener.updateNotificationChannel(pkg, user, channel) }
                .onSuccess { changed++ }
                .onFailure {
                    allSilent = false
                    Logger.e("channel-silence: update failed for ${channel.id} of $pkg", it)
                }
        }
        // 标记依据是「此刻该应用的渠道全都不发声了」，而不是「这次改了几个」——渠道可能在上一次
        // 运行里就已改哑（进程重启后内存缓存是空的）。若不这样，热路径会一直误以为还没静音，
        // 每条通知都白白 snooze 一次，通知也就跟着闪一下。
        if (allSilent) silenced.add(pkg)
        Logger.i("channel-silence: silenced $changed/${channels.size} channels of $pkg (allSilent=$allSilent)")
        return allSilent
    }

    /** 还原 [pkg] 被静音的渠道（声音/振动回到静音前的样子）。 */
    fun restore(listener: NotificationListenerService, pkg: String) {
        val saved = SilencedChannelStore.snapshotsFor(listener, pkg)
        if (saved.isEmpty()) return
        if (!isAvailable(listener)) {
            // 关联被解除后无法再写回——保留快照，等能力恢复时还有机会还原。
            Logger.w("channel-silence: cannot restore $pkg, association gone")
            return
        }
        val user = userHandle(saved.first().userId)
        val live = runCatching { listener.getNotificationChannels(pkg, user) }.getOrNull().orEmpty()
            .associateBy(NotificationChannel::getId)

        var restored = 0
        for (snap in saved) {
            val channel = live[snap.channelId] ?: continue
            channel.setSound(snap.soundUri?.let(android.net.Uri::parse), snap.audioAttributes())
            // 顺序要紧：NotificationChannel.setVibrationPattern(null) 会顺手把振动开关置为 false
            // （见其实现：mVibrationEnabled = pattern != null && pattern.isNotEmpty()）。
            // 因此必须先写 pattern、再写开关，否则「原本开着振动但没有自定义 pattern」的渠道
            // 会被还原成不振动。
            channel.vibrationPattern = snap.vibrationPattern
            channel.enableVibration(snap.vibrationEnabled)
            runCatching { listener.updateNotificationChannel(pkg, user, channel) }
                .onSuccess { restored++ }
                .onFailure { Logger.e("channel-silence: restore failed for ${snap.channelId} of $pkg", it) }
        }
        SilencedChannelStore.forget(listener, pkg)
        silenced.remove(pkg)
        Logger.i("channel-silence: restored $restored channels of $pkg")
    }

    /** 由用户空间 id 构造 UserHandle：uid = userId * 100000 + appId，取 appId=0 即可定位该用户。 */
    private fun userHandle(userId: Int): UserHandle =
        UserHandle.getUserHandleForUid(userId * 100_000)

    /** 该应用的渠道当前是否已被我们改哑（据此可跳过监听器那套 snooze 掐断）。 */
    fun isSilenced(pkg: String): Boolean = pkg in silenced

    /** 供设置页展示：当前有多少个应用的渠道处于被本应用静音的状态。 */
    fun silencedPackages(context: Context): Set<String> = SilencedChannelStore.packages(context)
}
