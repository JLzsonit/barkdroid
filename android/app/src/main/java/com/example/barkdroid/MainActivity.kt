package com.example.barkdroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.barkdroid.data.PushMsg
import com.example.barkdroid.data.Store
import com.example.barkdroid.net.NtfyClient
import com.example.barkdroid.push.PushState
import com.example.barkdroid.service.BuiltInSounds
import com.example.barkdroid.service.Notifier
import com.example.barkdroid.service.PushService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================ 设计规范 ============================ */

// 深空底色 + 紫罗兰强调，一体化暗色高级感
private val Bg = Color(0xFF0A0A10)
private val Card1 = Color(0xFF15151F)
private val Card2 = Color(0xFF1B1B27)
private val Accent = Color(0xFF7C6CFF)
private val AccentSoft = Color(0xFFA78BFA)
private val Teal = Color(0xFF2DD4BF)
private val OkGreen = Color(0xFF34D399)
private val WarnAmber = Color(0xFFFBBF24)
private val ErrRed = Color(0xFFF87171)
private val TextHigh = Color(0xFFF4F4FA)
private val TextMid = Color(0xFF9A9AB0)

private val CardShape = RoundedCornerShape(22.dp)
private val FieldShape = RoundedCornerShape(14.dp)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Teal,
    secondaryContainer = Accent.copy(alpha = 0.22f),
    onSecondaryContainer = AccentSoft,
    background = Bg,
    onBackground = TextHigh,
    surface = Card1,
    onSurface = TextHigh,
    surfaceVariant = Card2,
    onSurfaceVariant = TextMid,
    outline = Color(0x1FFFFFFF),
    outlineVariant = Color(0x14FFFFFF),
    error = ErrRed,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, color = TextHigh),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = TextHigh),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextHigh),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp, color = TextHigh),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = TextMid),
)

/* ============================ Activity ============================ */

class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "没有通知权限，短信到了也不会显示在通知栏", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Store.loadMessages(this)
        Store.topic(this)
        startReceiverService(this)
        askNotificationPermission()

        setContent {
            MaterialTheme(colorScheme = DarkColors, typography = AppTypography) {
                BarkdroidApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startReceiverService(this)
    }

    private fun startReceiverService(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PushService::class.java),
            )
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun BarkdroidApp() {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Card1) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("接收") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    label = { Text("消息") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> StatusScreen()
                1 -> MessagesScreen()
                else -> SettingsScreen()
            }
        }
    }
}

/* ============================ 通用组件 ============================ */

/** 呼吸状态点 */
@Composable
private fun PulsingDot(color: Color, big: Boolean = false) {
    val infinite = rememberInfiniteTransition(label = "dot")
    val glow by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val core: Dp = if (big) 10.dp else 9.dp
    val halo: Dp = if (big) 24.dp else 20.dp
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(halo)
                .clip(CircleShape)
                .background(color.copy(alpha = glow)),
        )
        Box(Modifier.size(core).clip(CircleShape).background(color))
    }
}

/** 统一卡片 */
@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Card1),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(Modifier.padding(18.dp), content = content) },
    )
}

/** 品牌圆角小 logo */
@Composable
private fun BrandMark(size: Dp = 42.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.31f))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF7A66F0), Color(0xFF4C39B8), Color(0xFF201A46))
                )
            ),
    ) {
        // 与桌面图标同款的双气泡：后层透光玻璃 + 前层实心气泡带尾巴
        Canvas(Modifier.fillMaxSize()) {
            val k = this.size.width / 108f
            fun dp(v: Float) = v * k
            drawRoundRect(
                color = Color.White.copy(alpha = 0.20f),
                topLeft = Offset(dp(30f), dp(30f)),
                size = Size(dp(29f), dp(23.5f)),
                cornerRadius = CornerRadius(dp(8.2f)),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(dp(36.5f), dp(44.5f)),
                size = Size(dp(38f), dp(30f)),
                cornerRadius = CornerRadius(dp(11f)),
            )
            drawPath(
                color = Color.White,
                path = Path().apply {
                    moveTo(dp(46.5f), dp(72f))
                    quadraticBezierTo(dp(41.5f), dp(76.5f), dp(40.2f), dp(80.2f))
                    quadraticBezierTo(dp(44.8f), dp(77.6f), dp(50.5f), dp(73.5f))
                    close()
                },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, good: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, color = TextMid)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (label == "频道" || label == "服务器" || label == "包名") FontFamily.Monospace else FontFamily.Default,
            color = if (good) TextHigh else ErrRed,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    ok: Boolean?,
    actionText: String?,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (ok) {
                        true -> OkGreen
                        false -> ErrRed
                        null -> WarnAmber
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextHigh)
            Text(
                subtitle,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = TextMid,
            )
        }
        if (actionText != null) {
            TextButton(onClick = onAction) { Text(actionText, fontSize = 12.5.sp, color = AccentSoft) }
        }
    }
}

@Composable
private fun TextRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.5.sp, color = TextHigh)
        TextButton(onClick = onClick) { Text("打开", fontSize = 12.5.sp, color = AccentSoft) }
    }
}

@Composable
private fun Step(index: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Surface(
            color = Accent.copy(alpha = 0.15f),
            shape = CircleShape,
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$index",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentSoft,
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(text, fontSize = 12.5.sp, lineHeight = 19.sp, color = TextHigh.copy(alpha = 0.92f))
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        color = Color(0xFF0F0F17),
        shape = FieldShape,
        border = BorderStroke(1.dp, Color(0x14A78BFA)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier.padding(13.dp),
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 17.sp,
            color = AccentSoft,
        )
    }
}

/* ============================ 接收页 ============================ */

@Composable
private fun StatusScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    var pushUrl by remember { mutableStateOf("") }
    var jsonTemplate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        pushUrl = Store.pushUrl(context)
        jsonTemplate = """
            {
              "topic": "${Store.topic(context)}",
              "title": "（发件人变量）",
              "message": "（短信内容变量）"
            }
        """.trimIndent()
    }

    val connected = PushState.connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))

        // 顶部品牌行
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column {
                Text("接收苹果短信", style = MaterialTheme.typography.titleLarge)
                Text("iPhone 短信实时转发到这台手机", fontSize = 11.sp, color = TextMid)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 状态大卡（渐变 + 呼吸灯）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(
                    Brush.linearGradient(
                        if (connected) listOf(Color(0xFF3D338C), Color(0xFF7C6CFF))
                        else listOf(Color(0xFF33333F), Color(0xFF494959)),
                    ),
                )
                .padding(22.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(if (connected) OkGreen else WarnAmber, big = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (connected) "在线接收中" else "正在连接…",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    if (connected) "短信会实时到达这台手机，锁屏和后台也收得到"
                    else "自动重试中，连上后照样能收到",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    MiniStat("已接收", "${PushState.received} 条")
                    MiniStat("已拦截", "${PushState.blocked} 条")
                    MiniStat("通道", PushState.transport)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 推送地址
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Teal),
                )
                Spacer(Modifier.width(8.dp))
                Text("你的推送地址", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "iPhone 快捷指令往这个地址发 POST，这台手机就会收到",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = TextMid,
            )
            Spacer(Modifier.height(12.dp))
            CodeBlock(pushUrl)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(pushUrl))
                    Toast.makeText(context, "推送地址已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = FieldShape,
            ) {
                Text("复制推送地址", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 快捷指令配置
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentSoft),
                )
                Spacer(Modifier.width(8.dp))
                Text("快捷指令怎么配", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            }
            Spacer(Modifier.height(12.dp))

            Step(1, "快捷指令 App 右上角 + 新建，命名「短信→安卓」；再打开底部「自动化」→ 新建 → 选「信息」")
            Step(2, "添加动作「获取 URL 内容」，地址填 ${Store.server(context)}/ （只有服务器根地址，不带频道名）")
            Step(3, "展开后：方法选 POST；请求头部添加一条 Content-Type = application/json；请求体选 JSON，填三个字段（见下方，topic 已自动填好本机频道名）")
            Step(4, "title 和 message 的值换成快捷指令里的变量：title 填发件人、message 填短信内容")

            Spacer(Modifier.height(12.dp))
            CodeBlock(jsonTemplate)
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(jsonTemplate))
                    Toast.makeText(context, "JSON 已复制，粘贴到快捷指令请求体里", Toast.LENGTH_LONG).show()
                },
            ) { Text("复制 JSON（含本机频道名）", fontSize = 12.5.sp, color = AccentSoft) }

            Spacer(Modifier.height(4.dp))
            Text(
                "最后一步：回到自动化，选择刚建的「短信→安卓」指令，关闭「运行前询问」",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = TextMid,
            )
        }

        Spacer(Modifier.height(14.dp))

        // 权限
        PanelCard {
            Text("必要权限", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            Spacer(Modifier.height(12.dp))

            val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

            ActionRow(
                title = "通知权限",
                subtitle = if (notifOk) "已允许，短信会显示在通知栏" else "未允许，短信到了不会提醒",
                ok = notifOk,
                actionText = if (notifOk) null else "去开启",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0x12FFFFFF))
            ActionRow(
                title = "通知声音 / 振动 / 横幅",
                subtitle = "收不到声音、不振动或没有弹窗时点这里，开启「允许打扰」、振动、横幅和声音",
                ok = null,
                actionText = "去设置",
                onAction = { openChannelSettings(context) },
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0x12FFFFFF))
            ActionRow(
                title = "后台不被杀",
                subtitle = "小米/华为/OPPO 必须把省电策略设为「无限制」并允许自启动，否则息屏后收不到",
                ok = null,
                actionText = "去设置",
                onAction = { openBatterySettings(context) },
            )
        }

        Spacer(Modifier.height(14.dp))

        // 诊断
        var expanded by remember { mutableStateOf(false) }
        PanelCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("诊断信息", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开", color = AccentSoft)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                InfoRow("服务器", Store.server(context), true)
                InfoRow("频道", Store.topic(context), true)
                InfoRow("传输方式", PushState.transport, connected)
                InfoRow("接收服务", if (PushState.serviceRunning) "运行中" else "未运行", PushState.serviceRunning)
                InfoRow("重连次数", "${PushState.reconnectCount} 次", PushState.reconnectCount == 0)
                InfoRow("已拦截", "${PushState.blocked} 条", true)
                InfoRow(
                    "最近收到",
                    if (PushState.lastMessageAt == 0L) "还没收到" else formatTime(PushState.lastMessageAt),
                    PushState.lastMessageAt > 0,
                )
                if (PushState.lastError.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = Color(0x14F87171),
                        shape = FieldShape,
                    ) {
                        Text(
                            "最近错误：${PushState.lastError}",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = ErrRed,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
    }
}

/* ============================ 消息页 ============================ */

@Composable
private fun MessagesScreen() {
    val context = LocalContext.current
    val list = Store.messages

    // 详情二级页：非空时整页显示
    var opened by remember { mutableStateOf<PushMsg?>(null) }
    val detail = opened
    if (detail != null) {
        MessageDetailScreen(detail) { opened = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("消息", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (list.isEmpty()) "还没有收到消息" else "共 ${list.size} 条",
                    fontSize = 11.5.sp,
                    color = TextMid,
                )
            }
            if (list.isNotEmpty()) {
                TextButton(onClick = { Store.clearMessages(context) }) {
                    Text("清空", color = ErrRed)
                }
            }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Card2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFF3D3D4D),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "收到的短信会显示在这里",
                        color = TextMid,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "去「设置」可以发一条测试推送试试",
                        color = Color(0xFF5B5B6E),
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id.ifEmpty { it.time.toString() } }) { msg ->
                    MessageCard(msg) { opened = msg }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(msg: PushMsg, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Card1),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            // 发件人头像（取第一个字符）
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    msg.title.take(1).ifEmpty { "?" },
                    color = AccentSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        msg.title.ifEmpty { "未知发件人" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextHigh,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatTime(msg.time),
                        fontSize = 10.5.sp,
                        color = Color(0xFF6B6B80),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("›", fontSize = 15.sp, color = Color(0xFF4A4A5E))
                }
                if (msg.body.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(msg.body, fontSize = 13.sp, lineHeight = 19.sp, color = TextHigh.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/* ============================ 短信详情二级页 ============================ */

@Composable
private fun MessageDetailScreen(msg: PushMsg, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val body = msg.body.ifEmpty { msg.title }
    val code = remember(msg.id, msg.body) { extractVerificationCode(body) }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题 + 时间
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextHigh)
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text("短信详情", style = MaterialTheme.typography.titleLarge)
                Text(formatTime(msg.time), fontSize = 11.5.sp, color = TextMid)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 发件人 + 正文
            Card(
                Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = Card1),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                msg.title.take(1).ifEmpty { "?" },
                                color = AccentSoft,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                msg.title.ifEmpty { "未知发件人" },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = TextHigh,
                            )
                            Text(
                                if (msg.body.isBlank()) "无正文" else "转发短信",
                                fontSize = 11.sp,
                                color = Color(0xFF6B6B80),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF262634))
                    Spacer(Modifier.height(14.dp))

                    // 正文可长按自由选中
                    SelectionContainer {
                        Text(
                            msg.body.ifEmpty { "（这条消息没有正文）" },
                            fontSize = 14.5.sp,
                            lineHeight = 23.sp,
                            color = TextHigh.copy(alpha = 0.92f),
                        )
                    }
                }
            }

            if (code != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.13f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("识别到验证码", fontSize = 11.5.sp, color = AccentSoft)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                code,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                                color = TextHigh,
                            )
                        }
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(code))
                            Toast.makeText(context, "验证码已复制", Toast.LENGTH_SHORT).show()
                        }) { Text("复制", color = AccentSoft) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // 底部操作
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = {
                    val c = code
                    if (c != null) {
                        clipboard.setText(AnnotatedString(c))
                        Toast.makeText(context, "验证码已复制", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "这条短信里没找到验证码", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = FieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (code != null) Accent else Card2,
                    contentColor = if (code != null) Color.White else TextMid,
                ),
            ) {
                Text("复制验证码", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(msg.body))
                    Toast.makeText(context, "全文已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = FieldShape,
                border = BorderStroke(1.dp, Color(0xFF2E2E40)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHigh),
            ) {
                Text("复制全文", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * 从短信正文里提取验证码。
 * 优先匹配「验证码 / 校验码 / 动态码 / code」之后紧跟的 4–8 位数字，
 * 其次退回「独立成段的 6 位数字」，都找不到返回 null。
 */
private fun extractVerificationCode(text: String): String? {
    if (text.isBlank()) return null
    val labeled = Regex(
        """(?:验证码|校验码|动态码|确认码|激活码|安全码|动态密码|口令|code|otp)\D{0,10}(?<!\d)(\d{4,8})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )
    labeled.find(text)?.let { return it.groupValues[1] }
    Regex("""(?<!\d)(\d{6})(?!\d)""").find(text)?.let { return it.groupValues[1] }
    return null
}

/* ============================ 设置页 ============================ */

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 拦截规则二级页：非空时整页显示规则编辑器
    var ruleEditor by remember { mutableStateOf<RuleKind?>(null) }

    // 通知铃声选择弹窗
    var showSoundPicker by remember { mutableStateOf(false) }

    var server by remember { mutableStateOf(Store.server(context)) }
    var topic by remember { mutableStateOf(Store.topic(context)) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    val editor = ruleEditor
    if (editor != null) {
        RuleEditorScreen(editor) { ruleEditor = null }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // 自检
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OkGreen),
                )
                Spacer(Modifier.width(8.dp))
                Text("接收自检", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "点下面按钮，App 会往你自己的频道发一条测试消息。收到通知就说明整条链路是通的。",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = TextMid,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    testing = true
                    result = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            NtfyClient.publishTest(
                                context,
                                "接收苹果短信 · 自检",
                                "这条能收到，说明 iPhone → 安卓 的通道已经打通了",
                            )
                        }
                        result = r
                        testing = false
                    }
                },
                enabled = !testing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = FieldShape,
            ) {
                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (testing) "发送中…" else "发一条测试消息", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    Notifier.messageNotification(
                        context,
                        PushMsg(
                            id = "local-test-${System.currentTimeMillis()}",
                            title = "接收苹果短信 · 通知测试",
                            body = "如果你看到了这条通知、有声音或振动，说明通知功能正常，问题不在通知，而在接收链路",
                            time = System.currentTimeMillis(),
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = FieldShape,
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("只测通知（声音 + 振动，不走网络）")
            }
            if (result.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFF0F0F17),
                    shape = FieldShape,
                ) {
                    Text(
                        result,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextMid,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 配置
        PanelCard {
            Text("接收配置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("频道名") },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("服务器地址") },
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val cleanServer = Store.normalizeServer(server)
                        if (cleanServer == null) {
                            Toast.makeText(
                                context,
                                "服务器地址要以 http:// 或 https:// 开头，例如 https://ntfy.sh",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@Button
                        }
                        if (!Store.isValidTopic(topic)) {
                            Toast.makeText(
                                context,
                                "频道名只能用字母、数字、- 和 _，1–64 位",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@Button
                        }
                        topic = topic.trim()
                        server = cleanServer
                        Store.setTopic(context, topic)
                        Store.setServer(context, cleanServer)
                        NtfyClient.restart(context)
                        Toast.makeText(context, "已保存并重连", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = FieldShape,
                ) { Text("保存", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = {
                        topic = Store.regenerateTopic(context)
                        NtfyClient.restart(context)
                        Toast.makeText(context, "已生成新频道，记得更新快捷指令地址", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = FieldShape,
                ) { Text("换一个频道") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "频道名相当于密码，别人知道就能给你发消息，建议保持随机串，不要改成容易猜的词",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = TextMid,
            )
        }

        Spacer(Modifier.height(14.dp))

        // 拦截规则（入口，点进去逐条管理）
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(WarnAmber),
                )
                Spacer(Modifier.width(8.dp))
                Text("拦截规则", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "命中的消息不弹通知、不进消息列表，直接丢弃；首页「已拦截」会显示次数",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = TextMid,
            )
            Spacer(Modifier.height(8.dp))

            RuleEntryRow(
                title = "拦截关键词",
                subtitle = "已设 ${Store.blockKeywords(context).size} 条 · 命中标题或正文即拦截",
            ) { ruleEditor = RuleKind.KEYWORD }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0x12FFFFFF))
            RuleEntryRow(
                title = "拦截号码",
                subtitle = "已设 ${Store.blockNumbers(context).size} 条 · 按发件人号码匹配",
            ) { ruleEditor = RuleKind.NUMBER }
        }

        Spacer(Modifier.height(14.dp))

        // 通知铃声
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Teal),
                )
                Spacer(Modifier.width(8.dp))
                Text("通知铃声", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            }
            Spacer(Modifier.height(8.dp))
            RuleEntryRow(
                title = BuiltInSounds.nameOf(Store.smsSound(context)),
                subtitle = "收到短信时响的声音，点开可以试听后更换",
            ) { showSoundPicker = true }
        }

        Spacer(Modifier.height(14.dp))

        PanelCard {
            Text("系统设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            Spacer(Modifier.height(6.dp))
            TextRow("通知设置") { openNotificationSettings(context) }
            TextRow("省电策略 / 后台限制") { openBatterySettings(context) }
            TextRow("应用详情") { openAppInfo(context) }
        }

        Spacer(Modifier.height(14.dp))

        PanelCard {
            Text("关于", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
            Spacer(Modifier.height(10.dp))
            InfoRow("版本", BuildConfig.VERSION_NAME, true)
            InfoRow("通道", "HTTP 长连接（ntfy 协议）", true)
            InfoRow("包名", context.packageName, true)
        }

        Spacer(Modifier.height(26.dp))
    }

    if (showSoundPicker) {
        var soundSel by remember { mutableStateOf(Store.smsSound(context)) }
        AlertDialog(
            onDismissRequest = {
                showSoundPicker = false
                previewSoundStop()
            },
            title = { Text("通知铃声") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "点一条即试听并应用，离开设置页后生效于新通知",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = TextMid,
                    )
                    Spacer(Modifier.height(6.dp))
                    BuiltInSounds.PRESETS.forEach { preset ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(FieldShape)
                                .clickable {
                                    soundSel = preset.id
                                    Store.setSmsSound(context, preset.id)
                                    runCatching { Notifier.ensureChannels(context) }
                                    previewSound(context, preset.id)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = soundSel == preset.id,
                                onClick = null,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(preset.name, fontSize = 14.sp, color = TextHigh)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSoundPicker = false
                    previewSoundStop()
                }) { Text("完成") }
            },
        )
    }
}

/* ============================ 拦截规则二级页 ============================ */

/** 两种规则类型的文案与校验逻辑 */
private enum class RuleKind(
    val title: String,
    val typeName: String,
    val inputLabel: String,
    val placeholder: String,
    val hint: String,
) {
    KEYWORD(
        "拦截关键词",
        "关键词",
        "输入关键词",
        "例如：贷款",
        "关键词不区分大小写，短信标题或正文包含该词就会被拦截；添加后即时生效",
    ),
    NUMBER(
        "拦截号码",
        "号码",
        "输入号码",
        "例如：10086",
        "号码只比对数字部分（忽略 +、空格、-），填 10086 可同时拦下 10086 和 +8610086",
    ),
}

/** 拦截规则编辑页：列表逐条展示，底部输入框逐条添加，点 × 删除，改动即时生效 */
@Composable
private fun RuleEditorScreen(kind: RuleKind, onBack: () -> Unit) {
    val context = LocalContext.current

    val rules = remember {
        mutableStateListOf<String>().apply {
            addAll(
                when (kind) {
                    RuleKind.KEYWORD -> Store.blockKeywords(context)
                    RuleKind.NUMBER -> Store.blockNumbers(context)
                },
            )
        }
    }
    var input by remember { mutableStateOf("") }

    fun tryAdd() {
        val v = input.trim()
        if (v.isEmpty()) {
            Toast.makeText(context, "${kind.typeName}不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (kind == RuleKind.NUMBER && v.filter { it.isDigit() }.isEmpty()) {
            Toast.makeText(context, "号码至少要包含一个数字", Toast.LENGTH_SHORT).show()
            return
        }
        val added = when (kind) {
            RuleKind.KEYWORD -> Store.addBlockKeyword(context, v)
            RuleKind.NUMBER -> Store.addBlockNumber(context, v)
        }
        if (added) {
            rules.add(v)
            input = ""
        } else {
            Toast.makeText(context, "这条${kind.typeName}已经加过了", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题 + 计数
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextHigh)
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(kind.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (rules.isEmpty()) "还没有规则" else "共 ${rules.size} 条",
                    fontSize = 11.5.sp,
                    color = TextMid,
                )
            }
        }

        if (rules.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(Card2),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(0xFF3D3D4D),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("在下方输入第一条${kind.typeName}", color = TextMid, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rules, key = { it }) { rule ->
                    RuleItemCard(rule) {
                        when (kind) {
                            RuleKind.KEYWORD -> Store.removeBlockKeyword(context, rule)
                            RuleKind.NUMBER -> Store.removeBlockNumber(context, rule)
                        }
                        rules.remove(rule)
                    }
                }
            }
        }

        // 底部添加行
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(kind.inputLabel) },
                placeholder = { Text(kind.placeholder) },
                singleLine = true,
                shape = FieldShape,
                keyboardOptions = if (kind == RuleKind.NUMBER) {
                    KeyboardOptions(keyboardType = KeyboardType.Phone)
                } else {
                    KeyboardOptions.Default
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { tryAdd() },
                modifier = Modifier.height(56.dp),
                shape = FieldShape,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }
        Text(
            kind.hint,
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = TextMid,
        )
    }
}

/** 单条规则卡片：内容 + 删除按钮 */
@Composable
private fun RuleItemCard(rule: String, onRemove: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = FieldShape,
        colors = CardDefaults.cardColors(containerColor = Card1),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rule,
                Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                fontSize = 13.5.sp,
                fontFamily = FontFamily.Monospace,
                color = TextHigh,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "删除", tint = TextMid)
            }
        }
    }
}

/** 设置页里的规则入口行 */
@Composable
private fun RuleEntryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = TextHigh)
            Text(subtitle, fontSize = 11.5.sp, lineHeight = 16.sp, color = TextMid)
        }
        TextButton(onClick = onClick) { Text("管理", fontSize = 12.5.sp, color = AccentSoft) }
    }
}

/* ============================ 工具方法 ============================ */

private var previewPlayer: MediaPlayer? = null

/** 试听铃声：播一次自动释放；换听下一条时先停掉上一条 */
private fun previewSound(context: Context, id: String) {
    runCatching {
        previewPlayer?.release()
        previewPlayer = null
        val mp = MediaPlayer()
        mp.setDataSource(context, BuiltInSounds.uriFor(context, id))
        mp.prepare()
        mp.setOnCompletionListener {
            it.release()
            if (previewPlayer === it) previewPlayer = null
        }
        mp.start()
        previewPlayer = mp
    }
}

private fun previewSoundStop() {
    runCatching {
        previewPlayer?.release()
    }
    previewPlayer = null
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.onFailure {
        Toast.makeText(context, "打不开系统设置，请手动去应用设置里开通知", Toast.LENGTH_LONG).show()
    }
}

private fun openChannelSettings(context: Context) {
    // 直接跳到「短信消息」通知渠道的设置页（允许打扰 / 声音 / 横幅都在这里）
    val ok = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.smsChannelId(context))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.isSuccess
    if (!ok) {
        Toast.makeText(context, "打不开渠道设置，请到 应用信息 → 通知 → 短信消息 里开启", Toast.LENGTH_LONG).show()
        openNotificationSettings(context)
    }
}

private fun openBatterySettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    Toast.makeText(context, "请手动在系统设置里找到本应用", Toast.LENGTH_LONG).show()
}

private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
