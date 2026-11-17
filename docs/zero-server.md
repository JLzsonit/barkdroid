> ⚠️ 已废弃：本项目已改用 ntfy 直连通道，请阅读 **ntfy-setup.md**。以下内容仅作历史参考。

# 零服务器模式：iPhone 快捷指令直连极光推送

没有自己的服务器？可以完全跳过 barkdroid 服务端：

```
iPhone 快捷指令 ──POST https://api.jpush.cn/v3/push──▶ 极光推送云
                                                          │ 系统级推送
                                                     安卓 App 收到通知
```

barkdroid 服务端本质只是"把 Bark 格式请求翻译成极光 REST API"，所以让快捷指令直接调极光 API 即可，中间服务器、域名、HTTPS 证书、公网 IP 全都不需要。

## 前提

1. 注册极光推送：https://www.jiguang.cn/console/
2. 创建一个**安卓应用**，得到：
   - **AppKey** — 填进安卓 `android/config.properties` 的 `JPUSH_APP_KEY`（同时用于下面快捷指令的鉴权）
   - **Master Secret** — 用于快捷指令的 Authorization 头（**不要**写进任何公开代码）
3. 用 GitHub Actions（本仓库自带工作流）编译 APK，装到安卓手机
4. 打开 App，点击复制**设备 Registration ID**

## 第一步：生成鉴权串

极光 REST API 用 HTTP Basic 认证，鉴权串 = `base64(AppKey + ":" + Master Secret)`。

最方便的做法是让快捷指令**动态生成**，不用自己算：

快捷指令里加一个「Base64 编码」动作：
1. 「文本」动作，内容填 `你的AppKey:你的MasterSecret`（中间的冒号不能少）
2. 「Base64 编码」动作（选"编码"）

得到形如 `MmE0Yj...` 的字符串，下一步直接引用这个变量。

## 第二步：快捷指令调用推送

在现有的"发短信转发"快捷指令中，把原来请求 Bark 地址的「获取 URL 内容」动作替换为：

- **URL**: `https://api.jpush.cn/v3/push`
- **方法**: POST
- **请求头**:
  - `Content-Type`: `application/json`
  - `Authorization`: `Basic ` + 上一步的 Base64 变量（注意 "Basic" 后有一个空格）
- **请求体 (JSON)**:

```json
{
  "platform": "android",
  "audience": {
    "registration_id": ["把 App 里复制的 Registration ID 填这里"]
  },
  "notification": {
    "android": {
      "alert": "短信正文（用快捷指令的短信内容变量）",
      "title": "发件人标题（用短信发件人变量）",
      "extras": { "url": "可选，点通知后打开的链接" }
    }
  }
}
```

「获取 URL 内容」动作自带 JSON 请求体编辑器，把 `alert` 和 `title` 的值换成快捷指令变量（快捷指令会自动加引号和转义）。

> 你原来 Bark 快捷指令里构造的 `标题` / `内容` 变量可以原样复用，只是投递目标从 Bark 服务器换成极光 API。

## 第三步：验证

1. 安卓 App 显示 `ready (direct mode)` 状态和 Registration ID
2. 手动运行一次快捷指令
3. 安卓应弹出系统通知；若通知里带了 `extras.url`，点击通知会打开对应链接

## 安卓端改动说明

相对原版 barkdroid 的修改（已在本仓库完成）：

- App 不再向自建服务器 `/register`，直接把 JPush `registration_id` 当作设备标识展示和复制
- 补充 Android 13+ 的 `POST_NOTIFICATIONS` 运行时权限申请（缺了它收不到通知横幅）
- `SERVER_BASE_URL` 配置保留但不再使用，留空即可

## 安全提示

- Master Secret 只存在于你 iPhone 的快捷指令里，不上传任何第三方（除了极光 API 本身），个人自用可接受
- 短信内容会经过极光服务器投递，如对隐私敏感可考虑后续给消息体加密（App 端解密）
- 如果以后想要 Bark 兼容 URL、多个设备管理、历史记录等功能，再补一个 Cloudflare Workers 免费版做兼容层即可，不用买服务器
