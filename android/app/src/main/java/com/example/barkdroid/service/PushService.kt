package com.example.barkdroid.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.barkdroid.data.Store
import com.example.barkdroid.net.NtfyClient
import com.example.barkdroid.push.PushState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 常驻前台服务：持有 ntfy 长连接，保证锁屏 / 后台 / 划掉最近任务后仍能收到短信。
 * 通知栏会有一条低优先级常驻通知（Android 要求，不能去掉）。
 */
class PushService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()

        Notifier.ensureChannels(this)

        // 首次启动：把同步起点设为当前时间，避免把服务器缓存的历史消息全拉下来
        if (Store.lastTs(this) == 0L) {
            Store.setLastTs(this, System.currentTimeMillis() / 1000)
        }

        startForeground(Notifier.SERVICE_NOTIF_ID, Notifier.serviceNotification(this, "正在连接…"))
        PushState.serviceRunning = true

        NtfyClient.start(this)
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统回收后自动重建
        if (!PushState.serviceRunning) {
            PushState.serviceRunning = true
            NtfyClient.start(this)
            startTicker()
        }
        return START_STICKY
    }

    /** 刷新常驻通知上的状态文字 */
    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(4000)
                val text = when {
                    PushState.connected -> {
                        val host = Store.server(this@PushService).removePrefix("https://").removePrefix("http://")
                        "已连接 $host · 已收 ${PushState.received} 条"
                    }
                    else -> "重连中…（收到的短信不会丢）"
                }
                runCatching {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(Notifier.SERVICE_NOTIF_ID, Notifier.serviceNotification(this@PushService, text))
                }
            }
        }
    }

    override fun onDestroy() {
        ticker?.cancel()
        NtfyClient.stop()
        PushState.serviceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
