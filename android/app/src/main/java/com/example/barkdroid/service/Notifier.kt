package com.example.barkdroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.barkdroid.MainActivity
import com.example.barkdroid.R
import com.example.barkdroid.data.PushMsg
import com.example.barkdroid.data.Store

/** 通知渠道与两类通知（常驻服务通知 + 短信通知） */
object Notifier {

    const val CH_SERVICE = "svc_channel"
    const val SERVICE_NOTIF_ID = 1001

    /**
     * 短信渠道 ID = 前缀 + 所选铃声 id。
     * Android 不允许修改已创建渠道的声音 / 振动配置，所以换铃声就换 ID 重建渠道，
     * 旧的自动清掉。v4 之前是固定 ID（sms_channel / sms_channel_v2 / sms_channel_v3）。
     */
    private const val CH_SMS_PREFIX = "sms_channel_v4_"
    private val LEGACY_SMS_CHANNELS = setOf("sms_channel", "sms_channel_v2", "sms_channel_v3")

    /** 当前短信通知渠道 ID（随所选铃声变化，渠道设置页跳转也用它） */
    fun smsChannelId(ctx: Context): String = CH_SMS_PREFIX + Store.smsSound(ctx)

    /**
     * 短信通知的振动节奏：立即开始，振 300ms → 停 200ms → 振 300ms。
     * 必须显式给 pattern —— 只调 enableVibration(true) 时，系统会去取渠道默认值，
     * 而一旦渠道同时设置了 sound，很多 ROM（MIUI / EMUI / ColorOS）就退化成静默不振动。
     */
    private val SMS_VIBRATION = longArrayOf(0, 300, 200, 300)

    private val SOUND_ATTRS = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "后台接收服务", NotificationManager.IMPORTANCE_MIN).apply {
                description = "保持在线接收短信，请勿关闭"
                setShowBadge(false)
            },
        )

        val soundId = Store.smsSound(ctx)
        nm.createNotificationChannel(
            NotificationChannel(smsChannelId(ctx), "短信消息", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "收到转发的短信时提醒 · 铃声：${BuiltInSounds.nameOf(soundId)}"
                // 振动：开关 + 显式节奏，两者都要有
                enableVibration(true)
                setVibrationPattern(SMS_VIBRATION)
                enableLights(true)
                setSound(BuiltInSounds.uriFor(ctx, soundId), SOUND_ATTRS)
            },
        )

        // 清理旧渠道：历史遗留的固定 ID + 换铃声后残留的旧指纹渠道
        val keep = setOf(CH_SERVICE, smsChannelId(ctx))
        runCatching {
            nm.notificationChannels.forEach { ch ->
                if (ch.id !in keep &&
                    (ch.id.startsWith("sms_channel") || ch.id in LEGACY_SMS_CHANNELS)
                ) {
                    nm.deleteNotificationChannel(ch.id)
                }
            }
        }
    }

    fun serviceNotification(ctx: Context, text: String): Notification {
        val pi = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle("接收苹果短信正在运行")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }

    fun messageNotification(ctx: Context, msg: PushMsg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val requestCode = msg.id.hashCode().let { if (it == 0) 1 else it }
        val pi = PendingIntent.getActivity(
            ctx,
            requestCode,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, smsChannelId(ctx))
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(msg.title.ifBlank { "新短信" })
            .setContentText(msg.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(requestCode, n) }
    }
}
