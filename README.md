# 静界 / Hush

一款原生 Android 通知管控应用:为通知定义**规则**,匹配到的通知执行**动作**——丢弃、静音、改写文本、调整重要性、自动回复、朗读、稍后提醒、聚合摘要、以滚动**弹幕**呈现、转发到 Webhook、运行 Tasker 任务等等。目标是还你一个更安静、更可控的通知栏。

本项目为净室(clean-room)实现,用 Kotlin + Jetpack Compose 从零编写,不含任何第三方应用的代码或素材,仅是对同类功能的原创实现。

> 包名为 `com.iccyuan.hush`;对用户展示的名称为 静界 / Hush。

## 功能

一条规则 = **应用** + **触发器** + **条件** + **动作**。

**应用范围** —— 选择特定应用(留空 = 全部)。选择器通过 LauncherApps 枚举所有用户配置下的可启动应用,因此**应用分身(应用双开)**会作为独立的「(分身)」格子出现;另有「显示系统应用」开关可显示无启动入口的系统包。

**触发器**(ALL/ANY 逻辑;留空 = 匹配所有通知)
- 文本匹配任意字段——标题、正文、展开文本、子/信息文本、ticker、应用名,以及**类别 / 渠道 / 发信人**元数据——支持 `包含 / 等于 / 开头 / 结尾 / 正则 / 通配符`、区分大小写与取反。
- 是否为常驻通知。
- 是否带内联回复操作(聊天类通知)。

**条件**(AND 逻辑的上下文判断)
- 一天中的时间 + 星期窗口(可跨午夜)。
- 中国法定节假日感知(法定假日 / 调休补班 / 周末 / 工作日),含「今天休息 / 今天上班」一键覆盖,节假日数据可联网刷新。
- 充电中 / 使用电池;屏幕亮 / 灭;电量高于 / 低于阈值。
- 冷却(限制规则触发频率)。

**动作**(按顺序执行)
- 丢弃(完全隐藏)/ 消除(可延时)/ 稍后提醒。
- 替换文本(字面或正则,支持 `$1` 反向引用)/ 用模板设置某字段。
- 调整重要性 + 绕过勿扰。
- 声音与振动覆盖(静音或自定义振动)。
- 通过通知的 RemoteInput 自动回复 / 朗读(TTS)/ 唤醒屏幕 / Toast。
- 摘要——把嘈杂通知收集起来,按时间窗汇总成一条。
- 静音来源应用 N 分钟 / 设置变量 / 运行 Tasker 任务 / 触发 Webhook。

**弹幕** —— 按规则开关:把命中的通知以半透明滚动悬浮条呈现,替代原生通知(需配合「丢弃」动作,并授予悬浮窗权限)。全局的**沉浸弹幕**(默认关闭)会在全屏(横屏看视频 / 玩游戏)时自动屏蔽原生通知、改以弹幕显示。

**模板** —— 动作文本支持占位符:`{title} {text} {bigtext} {subtext} {ticker} {app} {package}`、正则捕获 `{1}…{9}`、用户变量 `{var:name}`。

**其它**
- 总开关 + 快捷设置磁贴;按规则启用;「停止处理后续规则」。
- 通知历史(常驻 / 不可清除通知如 VPN 会被跳过)与统计洞察(总数、最吵应用、命中最多的规则)。
- 规则编辑器内含实时预览 + 交互式模拟器。
- 规则一键导出/导入(JSON);应用内对接 GitHub Releases 检查并**内置下载安装**更新。
- 浅色/深色/跟随系统主题,中英文,隐藏后台。

## 架构

```
data/    Room 实体 + DAO,密封的 Trigger/Condition/Action 模型
         (kotlinx.serialization,以 JSON 列存储),仓库,设置,
         节假日数据,已安装应用枚举 + 更新检查 + 内置安装器
engine/  纯粹、与 Android 无关的规则求值:RuleEngine、TextMatcher、
         TemplateEngine、VariableStore、Decision/MatchContext 等类型
service/ NotificationListenerService、通知重建/重发、渠道管理、TTS、
         副作用执行器、自动回复、弹幕悬浮窗、摘要、设备状态采样
ui/      Compose:规则列表、规则编辑器(触发器/条件/动作弹层 + 应用选择器)、
         历史、洞察、设置、主题、导航
util/    Logger 及共享工具
```

改写原理:NotificationListenerService 无法就地编辑其他应用的通知,因此命中的通知会**在本应用自己的渠道下被重建**(以控制提醒方式),原通知被取消,重建副本被重新发布——同时保留图标、内容 intent、操作与样式,并通过 substitute-name 附加项显示原应用名称。

OEM 提示:激进的省电策略(ColorOS / MIUI 等)可能杀掉监听器。本服务会在断连时、以及打开应用时请求重新绑定,设置页也实时显示**连接状态**(已连接 / 已断开 / 未授权)。为稳定运行,请将本应用加入电池优化白名单。

## 构建

需要 Android SDK(API 35)与 JDK 17+。

1. 用 **Android Studio**(Ladybug / 2024.2+)打开工程,或创建 `local.properties` 并写入 `sdk.dir=/path/to/Android/Sdk`。
2. 构建并运行 `app`,或在终端执行:
   ```
   ./gradlew assembleDebug          # 或 installDebug 安装到已连接设备
   ```
   产物命名为 `Hush-<版本>-<构建类型>.apk`。
3. 在设备上打开应用并授予**通知使用权**(*设置 → 通知 → 通知使用权 → 静界/Hush*)。弹幕还需授予「在其他应用上层显示」(ColorOS 上另需「后台弹出界面」)。

## 发布

推送形如 `v*` 的 git tag 即触发 GitHub Actions 发布流程:由 tag 推导版本号(`v1.2.3` → `1.2.3`),构建签名版 APK,并发布名为 **Hush-`<版本>`** 的 GitHub Release 并附上 APK。签名凭据来自仓库 Secrets(`KEYSTORE_BASE64`、`KEYSTORE_STORE_PASSWORD`、`KEYSTORE_KEY_ALIAS`、`KEYSTORE_KEY_PASSWORD`)。

## 权限

- **通知使用权**(核心;在系统设置中授予)。
- `POST_NOTIFICATIONS`、`VIBRATE`、`WAKE_LOCK`、`FOREGROUND_SERVICE` —— 用于重发/提醒通知及可靠的后台运行。
- `INTERNET` —— 节假日数据刷新、应用内检查更新、以及 Webhook 动作。
- `REQUEST_INSTALL_PACKAGES` —— 内置下载更新后拉起系统安装器。
- `SYSTEM_ALERT_WINDOW` —— 弹幕悬浮窗。
- `RECEIVE_BOOT_COMPLETED` —— 重启后恢复状态。
- `QUERY_ALL_PACKAGES` —— 在规则的应用选择器中列出应用。
