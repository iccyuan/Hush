package com.iccyuan.hush.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.iccyuan.hush.data.model.Importance
import com.iccyuan.hush.data.model.VibrationPreset
import com.iccyuan.hush.engine.SoundOverride

/**
 * 在 Android O 及以上版本，通知的重要性、声音和振动是其通知渠道的属性，而非通知本身的属性。
 * 为了实现按规则覆盖，我们会针对每个唯一的（重要性、声音、振动、勿扰）组合惰性地创建一个
 * 独立的通知渠道，并以一个签名键对其进行缓存。
 */
class ChannelManager(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)
    private val created = HashSet<String>()

    companion object {
        const val HISTORY_CHANNEL = "buzzkill_activity"
        const val DEFAULT_REPOST = "buzzkill_repost_default"
        const val DIGEST_CHANNEL = "buzzkill_digest"
    }

    fun ensureBaseChannels() {
        val activity = NotificationChannel(
            HISTORY_CHANNEL,
            context.getString(com.iccyuan.hush.R.string.channel_activity),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(com.iccyuan.hush.R.string.channel_activity_desc) }
        nm.createNotificationChannel(activity)

        val digest = NotificationChannel(
            DIGEST_CHANNEL,
            context.getString(com.iccyuan.hush.R.string.channel_digest),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(com.iccyuan.hush.R.string.channel_digest_desc) }
        nm.createNotificationChannel(digest)
    }

    /** 返回与所请求的提醒覆盖项相匹配的通知渠道 id。 */
    fun channelFor(
        importance: Importance?,
        sound: SoundOverride?,
        bypassDnd: Boolean,
    ): String {
        val imp = importance ?: Importance.DEFAULT
        val sig = buildString {
            append("repost_")
            append(imp.name)
            append("_silent").append(sound?.silent == true)
            append("_snd").append(sound?.soundUri ?: "default")
            append("_vib").append(sound?.vibration?.name ?: "default")
            append("_dnd").append(bypassDnd)
        }
        if (created.add(sig)) {
            createChannel(sig, imp, sound, bypassDnd)
        }
        return sig
    }

    private fun createChannel(
        id: String,
        importance: Importance,
        sound: SoundOverride?,
        bypassDnd: Boolean,
    ) {
        val channel = NotificationChannel(id, channelName(importance), importance.toPlatform())
        when {
            sound?.silent == true -> {
                channel.setSound(null, null)
                channel.enableVibration(false)
            }
            else -> {
                sound?.soundUri?.let {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    channel.setSound(Uri.parse(it), attrs)
                }
                sound?.vibration?.let { preset ->
                    if (preset == VibrationPreset.NONE) {
                        channel.enableVibration(false)
                    } else {
                        channel.enableVibration(true)
                        channel.vibrationPattern = preset.pattern
                    }
                }
            }
        }
        // 尽力而为地绕过勿扰模式；在没有策略访问权限时会被静默忽略。
        try {
            channel.setBypassDnd(bypassDnd || importance == Importance.URGENT)
        } catch (_: Exception) {
        }
        nm.createNotificationChannel(channel)
    }

    private fun channelName(importance: Importance) =
        context.getString(com.iccyuan.hush.R.string.channel_modified, importance.name.lowercase())

    private fun Importance.toPlatform(): Int = when (this) {
        Importance.MIN -> NotificationManager.IMPORTANCE_MIN
        Importance.LOW -> NotificationManager.IMPORTANCE_LOW
        Importance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
        Importance.HIGH -> NotificationManager.IMPORTANCE_HIGH
        Importance.URGENT -> NotificationManager.IMPORTANCE_HIGH
    }
}
