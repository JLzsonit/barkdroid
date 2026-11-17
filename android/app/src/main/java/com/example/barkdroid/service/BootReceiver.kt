package com.example.barkdroid.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 开机后自动恢复接收服务 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PushService::class.java))
            }
        }
    }
}
