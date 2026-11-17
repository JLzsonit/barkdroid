> ⚠️ 已废弃：本项目已改用 ntfy 直连通道，请阅读 **ntfy-setup.md**。以下内容仅作历史参考。

# iPhone「短信 → 安卓」快捷指令配置指南

> 目标：在发短信的 iPhone 上新建一条**独立的**自动化，把短信转发到安卓。
> 原有的「短信 → Bark → 另一台 iPhone」快捷指令**完全不动**，两条链路互不影响。

## 前置条件（一次性）

1. 注册极光推送：https://www.jiguang.cn/console/ → 创建**安卓应用**，记下 **AppKey** 和 **Master Secret**
2. 安卓手机装好 barkdroid APK（GitHub Actions 编译产物），打开 App 复制 **Registration ID**

## 第一步：新建快捷指令「短信→安卓」

打开快捷指令 App → 快捷指令 → 右上角 **+** 新建，依次添加以下动作：

### 动作 1：Base64 编码鉴权串

1. 添加「**文本**」动作，内容填：
   ```
   你的AppKey:你的MasterSecret
   ```
   （注意中间的英文冒号不能少）
2. 添加「**Base64 编码**」动作（选「编码 Base64」）

> 这一步输出形如 `MmE0Yj...` 的变量，后面 Authorization 头直接引用它。
> 也可以用 Mac/在线工具先算好固定值，跳过这两个动作，但动态生成以后换 Secret 不用重算。

### 动作 2：POST 到极光推送 API

1. 添加「**获取 URL 内容**」动作：
   - **URL**：`https://api.jpush.cn/v3/push`
   - **方法**：改为 `POST`
2. 展开「请求头」，添加两条：
   - `Content-Type` = `application/json`
   - `Authorization` = `Basic `（Basic 后有一个空格）+ 上一步 Base64 变量
3. 「请求体」选 **JSON**，添加以下字段：

| 键路径 | 类型 | 值 |
|---|---|---|
| platform | 文本 | `android` |
| audience.registration_id | 数组 | 添加一项「文本」= 安卓 App 里复制的 Registration ID |
| notification.android.title | 文本 | 选择**短信发件人变量**（来自触发自动化的"快捷指令输入"） |
| notification.android.alert | 文本 | 选择**短信内容变量** |
| notification.android.extras.url | 文本 | （可选）点击通知后打开的链接，不需要可删 |

> 「获取 URL 内容」的 JSON 编辑器里点字段值 → 「选择变量」即可引用快捷指令输入。

## 第二步：新建自动化触发

1. 快捷指令 App → **自动化** → **+** → 新建个人自动化
2. 触发条件选「**信息**」→「**收到信息时**」
   - 发件人：留空 = 所有来信（如只想转发特定号码可填选号码）
3. 运行方式：选「**立即运行**」并关闭「运行前询问」（与你现有 Bark 自动化保持一致）
4. 选择刚创建的快捷指令「短信→安卓」

## 第三步：测试

1. 安卓 App 状态应显示 `ready (direct mode)`，通知权限已授予
2. 用另一台手机给这台 iPhone 发一条短信
3. 检查：
   - iPhone B 的 Bark 收到（原有链路，不受影响）
   - 安卓手机收到系统通知：标题=发件人，正文=短信内容
4. 若安卓没收到：看极光控制台该应用「推送测试」页能否手动推成功，能则问题在快捷指令（检查 Authorization 的 `Basic ` 前缀空格、registration_id 是否复制完整）；不能则检查包名是否与极光控制台一致、签名是否为 debug 签名对应的应用配置

## 常见坑

- **Authorization 头格式**：必须是 `Basic` + 一个空格 + Base64 串，整个作为一个头的值
- **JSON 数组字段**：`audience.registration_id` 是数组，编辑器里先添加「数组」再在其中加「文本」项，或直接在 JSON 预览里手写
- **特殊字符**：极光 API 是 JSON 请求体，「获取 URL 内容」会自动处理转义，无需手动 URL 编码（这点比 Bark 的 URL 拼接省心）
- **Android 13+**：首次打开 App 会弹通知权限，必须允许
- **极光免费额度**：个人日常短信转发完全够用，具体限额以极光控制台显示为准
