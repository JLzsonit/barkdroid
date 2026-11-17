package com.example.barkdroid.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.barkdroid.data.PushMsg
import com.example.barkdroid.data.Store
import com.example.barkdroid.push.PushState
import com.example.barkdroid.service.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * ntfy 接收端。
 *
 * 主通道：SSE 长连接（HTTP/2 或 1.1 流式响应，走 443 标准端口，建立后秒级到达）
 * 兜底：长连接不可用时自动降级为每 8 秒轮询一次（多数网络环境下至少能保证到达）
 *
 * 不依赖任何厂商推送通道、不需要账号、不需要服务器，iPhone 端只要一次普通 HTTP POST。
 */
object NtfyClient {

    private val main = Handler(Looper.getMainLooper())
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /** 服务端 keepalive 约 45 秒一次，留 2.5 倍冗余：这么久一个字节都没来，就认定连接已死 */
    private const val SSE_READ_TIMEOUT_SEC = 120L

    /** 长连接正常时的低频保险轮询（覆盖半开连接盲区） */
    private const val POLL_SAFETY_MS = 60_000L

    /** 长连接断开时的兜底轮询间隔 */
    private const val POLL_BACKUP_MS = 8_000L

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // 一次性请求
        .retryOnConnectionFailure(true)
        .build()

    /**
     * SSE 专用 client：读超时绝不能是 0。
     *
     * readTimeout = 0 时，连接被 NAT / 运营商 / Wi-Fi 切换静默掐断（对端不发 FIN/RST）会让
     * readUtf8Line() 永远阻塞在旧的 socket 上：界面继续显示「在线接收中」，消息却再也收不到，
     * 而且永远不会抛异常、永远不会重连。给它一个大于 keepalive 间隔的读超时，
     * 死连接就会在 120 秒内变成 SocketTimeoutException，从而触发重连。
     */
    private val sseHttp: OkHttpClient = http.newBuilder()
        .readTimeout(SSE_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private var scope: CoroutineScope? = null
    private val seen: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())

    @Synchronized
    fun start(ctx: Context) {
        if (scope != null) return
        val app = ctx.applicationContext
        // 先把已存的历史消息 id 灌进来：否则进程每次重启后，since 会把最后一条消息
        // 再推一遍，用户会收到一条重复通知。
        runCatching { seen.addAll(Store.persistedIds(app)) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch { sseLoop(app) }
        s.launch { pollLoop(app) }
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
        main.post {
            PushState.connected = false
            PushState.transport = "已停止"
        }
    }

    /** 修改服务器 / 频道后重新建立连接 */
    fun restart(ctx: Context) {
        stop()
        val app = ctx.applicationContext
        main.post {
            PushState.transport = "启动中"
            PushState.lastError = ""
            start(app)
        }
    }

    /* ----------------------------- SSE 长连接 ----------------------------- */

    private suspend fun sseLoop(ctx: Context) {
        var backoff = 800L
        while (currentCoroutineContext().isActive) {
            val server = Store.server(ctx)
            val topic = Store.topic(ctx)
            val since = Store.lastTs(ctx)
            val url = "$server/$topic/sse" + if (since > 0) "?since=$since" else ""
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .build()

                sseHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("服务器返回 HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("响应为空")
                    val source = body.source()

                    main.post {
                        PushState.connected = true
                        PushState.transport = "实时长连接"
                        PushState.connectedSince = System.currentTimeMillis()
                        PushState.lastError = ""
                    }
                    backoff = 800L

                    while (currentCoroutineContext().isActive) {
                        // 服务端每 45 秒左右会发一帧 keepalive，所以这里能安全地设读超时；
                        // 超时 = 连接已经死了，抛异常走重连，而不是傻等。
                        val line = source.readUtf8Line() ?: throw IOException("连接被服务器关闭")
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty()) continue
                        // keepalive 帧由 handleJson 里的 event 判断自然丢弃，这里不要用子串匹配，
                        // 否则正文里出现 "keepalive" 字样的短信会被误吞。
                        handleJson(ctx, payload)
                    }
                }
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) break
                val reason = when (e) {
                    is SocketTimeoutException ->
                        "连接已静默失效（${SSE_READ_TIMEOUT_SEC} 秒无数据），正在重连"
                    else -> e.message ?: e.javaClass.simpleName
                }
                main.post {
                    PushState.connected = false
                    PushState.transport = "重连中"
                    PushState.reconnectCount += 1
                    PushState.lastError = reason
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(20_000L)
            }
        }
    }

    /* ------------------------------ 轮询兜底 ------------------------------ */

    private suspend fun pollLoop(ctx: Context) {
        while (currentCoroutineContext().isActive) {
            // 长连接正常时也保持低频轮询（保险检查）：半开连接在被读超时揪出来之前，
            // 仍然有最长 120 秒的盲区，这层兜底保证这段时间到的消息晚一点也一定送达。
            // 长连接断开时加密到 8 秒一次。
            delay(if (PushState.connected) POLL_SAFETY_MS else POLL_BACKUP_MS)
            runCatching { pollOnce(ctx) }.onFailure { e ->
                // 长连接正常时轮询失败不必打扰用户（可能只是这一次请求抖了）
                if (!PushState.connected) {
                    main.post { PushState.lastError = "轮询失败：${e.message ?: e.javaClass.simpleName}" }
                }
            }
        }
    }

    private fun pollOnce(ctx: Context) {
        val server = Store.server(ctx)
        val topic = Store.topic(ctx)
        val since = Store.lastTs(ctx)
        val url = "$server/$topic/json?poll=1&since=$since"
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            text.lineSequence().forEach { line ->
                if (line.isNotBlank()) handleJson(ctx, line)
            }
        }
    }

    /* ------------------------------ 消息处理 ------------------------------ */

    private fun handleJson(ctx: Context, raw: String) {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (o.optString("event", "message") != "message") return

        val id = o.optString("id")
        val timeSec = o.optLong("time", System.currentTimeMillis() / 1000)
        val title = o.optString("title").ifBlank { "新短信" }
        val body = o.optString("message")
        if (body.isBlank() && title.isBlank()) return

        synchronized(seen) {
            if (id.isNotEmpty() && !seen.add(id)) return
            if (seen.size > 600) {
                val cursor = seen.iterator()
                repeat(300) { if (cursor.hasNext()) { cursor.next(); cursor.remove() } }
            }
        }

        if (timeSec > Store.lastTs(ctx)) Store.setLastTs(ctx, timeSec)

        val msg = PushMsg(
            id = id.ifEmpty { timeSec.toString() },
            title = title,
            body = body,
            time = if (timeSec > 1_000_000_000L) timeSec * 1000 else System.currentTimeMillis(),
        )

        // 拦截规则：命中的消息不落库、不弹通知，只计数
        if (Store.shouldBlock(ctx, msg.title, msg.body)) {
            main.post { PushState.blocked += 1 }
            return
        }

        Store.addMessage(ctx, msg)
        Notifier.messageNotification(ctx, msg)

        main.post {
            PushState.received += 1
            PushState.lastMessageAt = msg.time
            if (!PushState.connected) PushState.transport = "定时轮询"
        }
    }

    /** 供设置页自检使用：向自己的频道发一条 */
    fun publishTest(ctx: Context, title: String, message: String): String {
        val server = Store.server(ctx)
        val topic = Store.topic(ctx)
        val payload = JSONObject().apply {
            put("topic", topic)
            put("title", title)
            put("message", message)
            put("priority", 4)
            put("tags", org.json.JSONArray().put("sms"))
        }
        val req = Request.Builder()
            .url("$server/")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    "发送成功（HTTP ${resp.code}）\n$text"
                } else {
                    "发送失败（HTTP ${resp.code}）\n$text"
                }
            }
        }.getOrElse { "请求失败：${it.message}" }
    }

    /** 仅测试连通性，不发消息 */
    fun checkServer(server: String): String {
        val req = Request.Builder().url("$server/v1/health").build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty().take(200)
                "HTTP ${resp.code} $text"
            }
        }.getOrElse { "无法连接：${it.message}" }
    }
}
