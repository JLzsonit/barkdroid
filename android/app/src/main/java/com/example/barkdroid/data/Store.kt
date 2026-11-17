package com.example.barkdroid.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** 一条收到的推送消息 */
data class PushMsg(
    val id: String,
    val title: String,
    val body: String,
    val time: Long,
)

/**
 * 本地存储：ntfy 服务器 / 频道名 / 上次同步时间 / 消息历史。
 * 全部落在 SharedPreferences，不需要数据库。
 */
object Store {

    private const val PREF = "barkdroid_prefs"
    private const val KEY_MSGS = "messages"
    private const val KEY_TOPIC = "ntfy_topic"
    private const val KEY_SERVER = "ntfy_server"
    private const val KEY_LAST_TS = "last_ts"
    private const val KEY_BLOCK_KEYWORDS = "block_keywords"
    private const val KEY_BLOCK_NUMBERS = "block_numbers"
    private const val KEY_SMS_SOUND = "sms_sound"

    private const val DEFAULT_TOPIC_PREFIX = "sms-"

    /** 消息列表，Compose 可直接观察 */
    val messages = mutableStateListOf<PushMsg>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /* ------------------------- 服务器 / 频道 ------------------------- */

    /** ntfy 频道名规则：只允许字母、数字、- 和 _，长度 1–64 */
    private val TOPIC_PATTERN = Regex("^[-_A-Za-z0-9]{1,64}$")

    fun isValidTopic(value: String): Boolean = TOPIC_PATTERN.matches(value.trim())

    /**
     * 规范化服务器地址：必须以 http:// 或 https:// 开头、带主机名、不能有空格。
     * 不合法返回 null（调用方负责提示用户）。
     */
    fun normalizeServer(raw: String): String? {
        val s = raw.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) return null
        val authority = s.substringAfter("://")
        if (authority.isBlank() || authority.contains(' ') || authority.startsWith("/")) return null
        return s
    }

    fun server(ctx: Context): String {
        val saved = prefs(ctx).getString(KEY_SERVER, "").orEmpty()
        return normalizeServer(saved) ?: BuildConfigDefault.SERVER.trimEnd('/')
    }

    fun setServer(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_SERVER, normalizeServer(value) ?: value.trim().trimEnd('/')).apply()
    }

    /** 频道名：首次访问自动生成，之后保持不变；历史版本留下的非法频道名会被重新生成 */
    fun topic(ctx: Context): String {
        val saved = prefs(ctx).getString(KEY_TOPIC, "").orEmpty()
        if (isValidTopic(saved)) return saved.trim()
        val generated = DEFAULT_TOPIC_PREFIX + randomHex(10)
        prefs(ctx).edit().putString(KEY_TOPIC, generated).apply()
        return generated
    }

    fun setTopic(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_TOPIC, value.trim()).apply()
    }

    /** 重新生成随机频道（换一个别人猜不到的地址） */
    fun regenerateTopic(ctx: Context): String {
        val generated = DEFAULT_TOPIC_PREFIX + randomHex(10)
        prefs(ctx).edit().putString(KEY_TOPIC, generated).apply()
        return generated
    }

    /** App 展示给用户直接复制的完整推送地址 */
    fun pushUrl(ctx: Context): String = "${server(ctx)}/${topic(ctx)}"

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /* ------------------------- 同步时间 ------------------------- */

    fun lastTs(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_TS, 0L)

    fun setLastTs(ctx: Context, ts: Long) {
        prefs(ctx).edit().putLong(KEY_LAST_TS, ts).apply()
    }

    /* ------------------------- 通知铃声 ------------------------- */

    /** 当前短信通知铃声 id（BuiltInSounds.PRESETS 之一） */
    fun smsSound(ctx: Context): String =
        prefs(ctx).getString(KEY_SMS_SOUND, "default").orEmpty().ifBlank { "default" }

    fun setSmsSound(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_SMS_SOUND, id).apply()
    }

    /* ------------------------- 拦截规则 ------------------------- */

    /** 关键词列表：一行一个。命中标题或正文（不区分大小写）即拦截 */
    fun blockKeywords(ctx: Context): List<String> = ruleLines(ctx, KEY_BLOCK_KEYWORDS)

    /** 拦截号码列表：一行一个。与发件人匹配即拦截 */
    fun blockNumbers(ctx: Context): List<String> = ruleLines(ctx, KEY_BLOCK_NUMBERS)

    /** 添加拦截关键词；为空或已存在（忽略大小写）返回 false */
    fun addBlockKeyword(ctx: Context, value: String): Boolean = addRule(ctx, KEY_BLOCK_KEYWORDS, value)

    /** 添加拦截号码；为空或已存在返回 false */
    fun addBlockNumber(ctx: Context, value: String): Boolean = addRule(ctx, KEY_BLOCK_NUMBERS, value)

    fun removeBlockKeyword(ctx: Context, value: String) = removeRule(ctx, KEY_BLOCK_KEYWORDS, value)

    fun removeBlockNumber(ctx: Context, value: String) = removeRule(ctx, KEY_BLOCK_NUMBERS, value)

    private fun addRule(ctx: Context, key: String, value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return false
        val current = ruleLines(ctx, key)
        if (current.any { it.equals(v, ignoreCase = true) }) return false
        prefs(ctx).edit().putString(key, (current + v).joinToString("\n")).apply()
        return true
    }

    private fun removeRule(ctx: Context, key: String, value: String) {
        val current = ruleLines(ctx, key).toMutableList()
        current.removeAll { it.equals(value.trim(), ignoreCase = true) }
        prefs(ctx).edit().putString(key, current.joinToString("\n")).apply()
    }

    private fun ruleLines(ctx: Context, key: String): List<String> {
        // 忽略大小写去重：旧版本一次粘贴多行时可能存过仅大小写不同的重复项，
        // 这类重复会让编辑页 LazyColumn 的 key 冲突直接崩溃
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        prefs(ctx).getString(key, "").orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { if (seen.add(it.lowercase())) out.add(it) }
        return out
    }

    /**
     * 是否命中拦截规则。
     * - 关键词：不区分大小写，标题或正文包含即命中；
     * - 号码：只比对数字部分（忽略 +、空格、-），发件人等于或以规则号码结尾即命中
     *   （这样 10086 能同时拦下 10086 和 +8610086 这类带国家码的写法）。
     * 可从任意线程调用（收消息在 IO 线程）。
     */
    fun shouldBlock(ctx: Context, title: String, body: String): Boolean {
        val keywords = blockKeywords(ctx)
        if (keywords.isNotEmpty()) {
            val t = title.lowercase()
            val b = body.lowercase()
            if (keywords.any { k -> t.contains(k.lowercase()) || b.contains(k.lowercase()) }) return true
        }

        val numbers = blockNumbers(ctx)
        if (numbers.isNotEmpty()) {
            val sender = title.filter { it.isDigit() }
            if (sender.isNotEmpty()) {
                for (n in numbers) {
                    val rule = n.filter { it.isDigit() }
                    if (rule.isEmpty()) continue
                    if (sender == rule || sender.endsWith(rule)) return true
                }
            }
        }
        return false
    }

    /* ------------------------- 消息历史 ------------------------- */

    fun loadMessages(ctx: Context) {
        val raw = prefs(ctx).getString(KEY_MSGS, "").orEmpty()
        messages.clear()
        if (raw.isEmpty()) return
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                messages.add(
                    PushMsg(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        body = o.optString("body"),
                        time = o.optLong("time"),
                    )
                )
            }
        }
    }

    /**
     * 已落盘的消息 id 集合。供接收端在启动时预热去重表用：
     * since 是「大于等于上次时间」，不加这层的话每次重启都会把最后一条消息重复弹一遍。
     * 只读 SharedPreferences，可从任意线程调用。
     */
    fun persistedIds(ctx: Context): Set<String> {
        val raw = prefs(ctx).getString(KEY_MSGS, "").orEmpty()
        if (raw.isEmpty()) return emptySet()
        val out = HashSet<String>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("id")
                if (id.isNotEmpty()) out.add(id)
            }
        }
        return out
    }

    private val main = Handler(Looper.getMainLooper())

    /** 可从任意线程调用：内部切回主线程改 Compose 状态 */
    fun addMessage(ctx: Context, msg: PushMsg) {
        val app = ctx.applicationContext
        main.post {
            if (msg.id.isNotEmpty() && messages.any { it.id == msg.id }) return@post
            messages.add(0, msg)
            if (messages.size > 200) messages.removeAt(messages.lastIndex)
            persist(app)
        }
    }

    fun clearMessages(ctx: Context) {
        messages.clear()
        persist(ctx)
    }

    private fun persist(ctx: Context) {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("title", m.title)
                    .put("body", m.body)
                    .put("time", m.time)
            )
        }
        prefs(ctx).edit().putString(KEY_MSGS, arr.toString()).apply()
    }
}

/** 避免 data 包直接依赖 BuildConfig 生成类的初始化顺序问题 */
internal object BuildConfigDefault {
    val SERVER: String = com.example.barkdroid.BuildConfig.DEFAULT_NTFY_SERVER.ifBlank { "https://ntfy.sh" }
}
