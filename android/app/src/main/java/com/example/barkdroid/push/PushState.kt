package com.example.barkdroid.push

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 接收通道的运行态，供界面直接观察。
 * 所有写入都通过主线程 post 过来，避免 Compose 快照错乱。
 */
object PushState {

    /** 长连接（SSE）是否已建立 */
    var connected by mutableStateOf(false)

    /** 当前使用的传输方式：实时长连接 / 定时轮询 / 未连接 */
    var transport by mutableStateOf("未连接")

    /** 最近一次建立连接的时间 */
    var connectedSince by mutableStateOf(0L)

    /** 最近一次收到消息的时间 */
    var lastMessageAt by mutableStateOf(0L)

    /** 累计重连次数 */
    var reconnectCount by mutableStateOf(0)

    /** 累计收到的消息条数（本次运行） */
    var received by mutableStateOf(0)

    /** 累计被拦截规则丢弃的消息条数（本次运行） */
    var blocked by mutableStateOf(0)

    /** 最近一次错误描述，空串表示没有错误 */
    var lastError by mutableStateOf("")

    /** 服务是否已启动 */
    var serviceRunning by mutableStateOf(false)
}
