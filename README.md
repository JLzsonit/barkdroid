<!-- dl-banner -->
> **下载安装**：[下载 APK（v2.4.0）](https://github.com/JLzsonit/barkdroid/releases/latest) · debug 构建，自用

# barkdroid

<!-- bilingual-intro -->
**中文**：**iPhone 短信转发到安卓的接收端**。iPhone 上用一条「收到信息」自动化把短信 POST 到 ntfy 频道，这台安卓手机上的 App 常驻接收并弹出通知。不依赖 FCM、不用注册账号、不用自建服务器。

**English**: The **Android receiving end** of an iPhone → Android SMS forwarding setup. A Shortcuts automation on the iPhone POSTs each incoming SMS to an ntfy topic; this app stays resident on the Android phone and turns them into notifications. No FCM, no account, no server to run.

| 项目 / Item | 说明 / Note |
|---|---|
| 名称 / Name | 接收苹果短信（桌面图标显示名）/ 接收苹果短信 (launcher label) |
| 角色 / Role | 接收端（安卓侧）/ Receiver (Android side) |
| 平台 / Platform | Android 8.0+（minSdk 26） |
| 安装 / Install | [Releases](https://github.com/JLzsonit/barkdroid/releases/latest) · APK（debug 构建）/ APK (debug build) |
| iPhone 端 / iOS side | 系统「快捷指令」，无需装 App / Shortcuts app, nothing to install |
| 许可证 / License | MIT |

## 这是什么

一句话：**把 iPhone 收到的短信，出现在安卓手机的通知栏里。**

整条链路只有两端——发送的 iPhone 和接收的这台安卓，中间只借 ntfy 做一次普通 HTTPS 中转：

- iPhone 端：系统自带「快捷指令」，不需要装任何 App
- 中间：ntfy 服务端（公共 `ntfy.sh`，也可以填自建实例）
- 安卓端：**本仓库**，一个常驻的接收端

典型场景是双持用户：主力机 iPhone 收验证码、快递、银行通知，备用安卓机在另一个房间/另一张卡上，需要同步看到。

## 它不做什么

- **不接管安卓本机的短信**：只显示 iPhone 转发过来的那些，不读取、不代发
- **不是通用推送平台**：没有注册、没有 API Key、没有多用户，频道名就是唯一凭据
- **不兼容 Bark API**：第一代确实做过 Bark 兼容 + 极光 JPush + 自建服务端，现已整体移除，代码留在 `server/` 存档
- **不保证军用级隐私**：公共 ntfy 实例上，频道名等于密码（见下方 FAQ）

## 工作方式

```
iPhone（收短信）
  │  「信息」自动化 → 快捷指令 → HTTP POST
  ▼
ntfy 服务端（ntfy.sh 或自建）
  │  SSE 长连接（秒级）   ┊  断线时轮询兜底
  ▼
┌──────────────────────────────┐
│  barkdroid（安卓接收端）        │
│  前台常驻服务 · 去重 · 拦截规则  │
└──────────────┬───────────────┘
               ▼
        通知栏「短信消息」
```

## 三步接入

完整图解版见 **[docs/ntfy-setup.md](docs/ntfy-setup.md)**，这里是压缩版。

**1. 装 App** — 把 APK 传到安卓手机安装，打开后允许通知权限。首页顶部出现 **● 在线接收中** 即表示已连上。

**2. 复制推送地址** — 首页「你的推送地址」卡片点「复制推送地址」，形如：

```
https://ntfy.sh/sms-3f9a1c7d02
```

频道名是首次启动随机生成的（`sms-` + 20 位十六进制），设置页可以换。

**3. iPhone 建快捷指令 + 自动化**

- 新建快捷指令 → 动作「获取 URL 内容」→ 地址填上面的推送地址 → 方法改 `POST` → 请求体填 `发件人：（发件人）　内容：（短信内容）`
- 想「标题=发件人、正文=短信内容」就用 JSON 方式（见下）
- 快捷指令 App → 自动化 → 「信息」→ 收到信息时 → 选择这条快捷指令 → 关闭「运行前询问」

## 消息格式

推荐 JSON POST 到服务器根地址（`https://ntfy.sh/`，不带频道名），中文不会乱码：

```json
{
  "topic": "sms-3f9a1c7d02",
  "title": "发件人",
  "message": "短信正文"
}
```

也可以直接 POST 纯文本到频道地址（`https://ntfy.sh/sms-3f9a1c7d02`），正文即消息内容，此时通知标题固定为「新短信」。命令行验证：

```bash
curl -d "测试正文" https://ntfy.sh/sms-3f9a1c7d02
curl -H "Title: 测试标题" -d "测试正文" https://ntfy.sh/sms-3f9a1c7d02
```

## 接收端做了什么

- **双通道接收**：SSE 长连接为主（通常 1 秒内到达）；连接正常时每 60 秒低频轮询兜底，断线时降到 8 秒一次
- **会自愈的死连接**：读超时设成 120 秒（服务端 keepalive 约 45 秒），被 NAT / 运营商 / Wi-Fi 切换静默掐断的半开连接会主动超时并重连，不会出现「界面显示在线但再也收不到」
- **不重复、不丢**：`since` 时间戳 + 消息 ID 双重去重，重启不会把最后一条再弹一遍；离线期间的消息联网后自动补收
- **常驻**：前台服务 + 开机自启，锁屏、划掉最近任务、重启手机都能继续收
- **拦截规则**：关键词命中标题或正文即静默丢弃；号码只比对数字部分，`10086` 能同时拦下 `+8610086` 这类写法
- **消息详情页**：点列表中任意一条进二级页，正文可长按自由选中复制；自动识别验证码并单独展示，一处点「复制验证码」、另一处「复制全文」
- **20 种内置提示音**：构建期合成的短音（`scripts/gen_tones.mjs`），换铃声会自动重建通知渠道（Android 不允许改已建渠道的声音）
- **可换服务器**：默认 `ntfy.sh`，支持自建 ntfy 实例，也允许明文 `http://`（内网自建常见情况）
- **自带诊断**：连接方式、重连次数、最近错误、被拦截条数，以及「发一条测试消息」自检

## 国产 ROM 必做

App 内「必要权限」卡片可以直接跳到对应设置页。小米 / 华为 / OPPO / vivo 上不做这几步，息屏后基本收不到：

1. 通知权限 → 允许
2. 省电策略 → **无限制**（默认「智能省电」会掐掉后台联网）
3. 自启动 → 允许
4. 后台弹出界面 → 允许（部分机型）
5. 最近任务里把 App **上滑锁定**

做完在「设置 → 接收自检」点一次「发一条测试消息」，能弹出通知就说明整条链路通了。

## 项目结构

```
android/   安卓接收端（Kotlin + Jetpack Compose）—— 当前唯一在维护的部分
server/    第一代自建服务端（Go + GoFrame + SQLite + JPush/Bark）—— 已废弃，仅存档
docs/      接入指南；quickstart.md / zero-server.md / shortcut-setup.md 均已废弃
scripts/   资源生成脚本：gen_tones.mjs 合成通知铃声，gen_icon.py 生成 Liquid Glass 图标
```

## 技术栈

| 组件 | 技术 |
|---|---|
| 接收端 | Kotlin + Jetpack Compose（单 Activity，Material 3） |
| 接收通道 | ntfy SSE 长连接 + 轮询兜底（OkHttp） |
| 本地存储 | SharedPreferences（频道、同步位点、消息历史、拦截规则、铃声） |
| 常驻 | 前台服务 `dataSync` + `BOOT_COMPLETED` 自启 |
| 构建 | AGP 8.7 / Kotlin 2.0 / Gradle 8.9，GitHub Actions 出 APK |

## FAQ

**为什么不用 FCM？**
国内安卓机型对 Google 服务的清理很激进，FCM 到达率不可用。ntfy 走标准 HTTPS 443，长连接 + 轮询双保险，不依赖任何厂商推送通道。

**为什么不用 Bark / PushDeer / Gotify？**
Bark 只服务 iOS；PushDeer 已停止维护且所有人共用一个厂商凭据；Gotify 需要自己长期跑一台公网服务器。本方案只需要一个免费 ntfy 实例（甚至公共实例），iPhone 端零安装。

**消息内容安全吗？**
传输全程 HTTPS。但公共 ntfy 实例上，**频道名就是访问凭据**——知道频道名的人既能读也能发。所以频道名是随机生成的，别外发；怀疑泄露就在设置页点「换一个频道」，然后同步更新快捷指令里的地址。

**iPhone 端要一直开着快捷指令吗？**
不用。自动化是系统级的，关掉 App 也会触发；但「运行前询问」必须关闭，否则每次要手动确认。

**安卓端耗电吗？**
一条长连接 + 每 60 秒一次轻量轮询，日常耗电可以忽略；真正的影响来自系统把后台网络掐掉，所以要按上面的「国产 ROM 必做」设置。

## License

[MIT](LICENSE)

> 早期版本曾基于 Go + GoFrame + 极光 JPush 做自建服务端与 Bark 兼容层，相关代码保留在 `server/` 供参考。
