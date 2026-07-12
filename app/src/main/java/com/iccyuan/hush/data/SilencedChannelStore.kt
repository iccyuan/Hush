package com.iccyuan.hush.data

import android.app.NotificationChannel
import android.content.Context
import android.media.AudioAttributes
import com.iccyuan.hush.data.db.BuzzJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * 渠道级静音的还原快照：静音一个应用前，先把它每个渠道的声音/振动原样存下来，
 * 解除静音时逐字段写回（见 [com.iccyuan.hush.service.ChannelSilencer]）。
 *
 * 这份数据必须比进程活得久——静音是长期状态，还原可能发生在几天后的另一次启动里。
 * 缺了它，用户的应用会被永久改哑，且无从恢复原本的提示音。
 */
object SilencedChannelStore {

    private const val PREFS = "hush_silenced_channels"

    @Serializable
    data class ChannelSnapshot(
        val channelId: String,
        /** 该应用所属用户空间（本体 0、分身如 999）——还原时要写回同一个用户下的渠道。 */
        val userId: Int = 0,
        val soundUri: String? = null,
        val audioUsage: Int = AudioAttributes.USAGE_NOTIFICATION,
        val audioContentType: Int = AudioAttributes.CONTENT_TYPE_SONIFICATION,
        val audioFlags: Int = 0,
        val vibrationEnabled: Boolean = false,
        val vibrationPattern: LongArray? = null,
    ) {
        fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(audioUsage)
            .setContentType(audioContentType)
            .setFlags(audioFlags)
            .build()

        // data class + LongArray：默认实现按引用比较，这里按内容比较，避免快照去重出错。
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChannelSnapshot) return false
            return channelId == other.channelId &&
                soundUri == other.soundUri &&
                audioUsage == other.audioUsage &&
                audioContentType == other.audioContentType &&
                audioFlags == other.audioFlags &&
                vibrationEnabled == other.vibrationEnabled &&
                vibrationPattern.contentEquals(other.vibrationPattern)
        }

        override fun hashCode(): Int =
            channelId.hashCode() * 31 + (vibrationPattern?.contentHashCode() ?: 0)
    }

    private val listSerializer = ListSerializer(ChannelSnapshot.serializer())

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 记下某渠道静音前的声音/振动设置。同一渠道重复静音时保留**最早**的那份（真正的原始值）。 */
    fun remember(context: Context, pkg: String, userId: Int, channel: NotificationChannel) {
        val existing = snapshotsFor(context, pkg)
        if (existing.any { it.channelId == channel.id }) return
        val snap = ChannelSnapshot(
            channelId = channel.id,
            userId = userId,
            soundUri = channel.sound?.toString(),
            audioUsage = channel.audioAttributes?.usage ?: AudioAttributes.USAGE_NOTIFICATION,
            audioContentType = channel.audioAttributes?.contentType
                ?: AudioAttributes.CONTENT_TYPE_SONIFICATION,
            audioFlags = channel.audioAttributes?.flags ?: 0,
            vibrationEnabled = channel.shouldVibrate(),
            vibrationPattern = channel.vibrationPattern,
        )
        write(context, pkg, existing + snap)
    }

    fun snapshotsFor(context: Context, pkg: String): List<ChannelSnapshot> {
        val json = prefs(context).getString(pkg, null) ?: return emptyList()
        return runCatching { BuzzJson.decodeFromString(listSerializer, json) }.getOrDefault(emptyList())
    }

    fun forget(context: Context, pkg: String) {
        prefs(context).edit().remove(pkg).apply()
    }

    fun packages(context: Context): Set<String> = prefs(context).all.keys

    private fun write(context: Context, pkg: String, snaps: List<ChannelSnapshot>) {
        runCatching {
            prefs(context).edit()
                .putString(pkg, BuzzJson.encodeToString(listSerializer, snaps))
                .apply()
        }
    }
}
