# AutoAuth For TUST

一个用于天津科技大学（TUST）校园网认证的 Android 自动登录工具。应用会在后台定时请求认证接口，帮助保持网络在线状态。

## 功能特性

- 账号、密码本地保存（SharedPreferences）
- 支持自定义 IPv4 / IPv6 参数
- 实时预览认证请求 URL
- 后台定时认证（默认每 10 秒执行一次）
- 前台服务常驻通知，可一键停止
- 可选开机自启（默认开启）
- 内置日志页面，支持实时刷新

## 运行环境

- Android Studio（推荐最新稳定版）
- JDK 11
- Android SDK：
  - `minSdk = 26`
  - `targetSdk = 35`
  - `compileSdk = 35`

## 快速开始

1. 克隆仓库并用 Android Studio 打开项目根目录。
2. 等待 Gradle 同步完成。
3. 连接设备或启动模拟器。
4. 运行 `app` 模块。
5. 在应用内填写账号、密码与（可选）自定义 IP 参数，点击“启动”。

## 构建与测试

在项目根目录执行：

```bash
./gradlew assembleDebug
./gradlew test
```

## 使用说明

### 主页面

- **账号 / 密码**：认证必填。
- **自定义 IPv4 / IPv6**：按需填写；为空时将以空参数发送。
- **开机自启开关**：控制是否在开机后自动启动认证服务。
- **启动 / 停止**：控制后台认证服务状态。
- **查看日志**：进入日志页面查看运行记录。

### 后台认证逻辑

- 服务启动后会周期性访问认证地址：
  `http://10.10.102.50:801/eportal/portal/login`
- 请求参数包含账号、密码、IPv4、IPv6 等信息。
- 结果会解析并通过页面状态与日志显示。

### 网络状态检测

- 应用通过访问 `https://www.baidu.com/` 粗略判断“已联网 / 未联网”。

## 权限说明

应用在 `AndroidManifest.xml` 中声明了以下权限：

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `RECEIVE_BOOT_COMPLETED`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`

## 日志说明

- 日志文件位置：`/data/data/<包名>/files/autoauth/log.txt`
- 体积超过约 1MB 时会轮转到 `log.txt.1`

## 项目结构

```text
app/src/main/java/com/example/autoauth/
├── MainActivity.kt      # 主界面与交互逻辑
├── AuthService.kt       # 后台认证服务
├── BootReceiver.kt      # 开机广播接收器
├── NetUtil.kt           # 网络与URL构造工具
├── LogUtil.kt           # 日志读写工具
└── LogActivity.kt       # 日志展示页面
```

## 注意事项

- 本项目为校园网特定场景工具，请仅在合法合规前提下使用。
- 认证接口、参数规则或网络环境变化可能导致失效，需要按实际情况调整。
- 当前仓库未集成自动发版流程，建议自行构建 APK 并本地安装验证。

