![Olauncher](https://repository-images.githubusercontent.com/278638069/db0acb80-661b-11eb-803e-926cae5dccb4)


# Olauncher | Minimal AF Launcher
AF stands for Ad-Free! :D

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Play Store"
    height="80" align="middle">](https://play.google.com/store/apps/details?id=app.olauncher)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80" align="middle">](https://f-droid.org/packages/app.olauncher)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png"
    alt="Get it on IzzyOnDroid"
    height="54" hspace="13" align="middle">](https://apt.izzysoft.de/packages/app.olauncher)

#### Install using [Play Store,](https://play.google.com/store/apps/details?id=app.olauncher) [F-Droid,](https://f-droid.org/packages/app.olauncher) [IzzyOnDroid](https://apt.izzysoft.de/packages/app.olauncher) or the [latest APK.](https://github.com/tanujnotes/Olauncher/releases/)

To maintain the simplicity of the launcher, a few niche features are available but hidden.

Please check out the **[About](https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher?utm_source=github)** page in the Olauncher settings for a complete list of features and **FAQs**.

##

License: [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

Developer: [@tanujnotes](https://x.com/tanujnotes)

##

### Other apps from team Olauncher:

- [Pro Launcher](https://play.google.com/store/apps/details?id=app.prolauncher) - Pro version of Olauncher with extra features like widgets, weather, folders, etc.

- [Note to Self](https://play.google.com/store/apps/details?id=com.makenotetoself) - Free and [open source](https://github.com/jeerovan/ntsapp) notes app with chat like interface and end-to-end encryption.

- [Pentastic](https://play.google.com/store/apps/details?id=app.pentastic) - Free and [open source](https://github.com/tanujnotes/Pentastic). A minimal tasks, notes and reminders app.

- [Supernova](https://github.com/tanujnotes/Supernova) - Free and [open source](https://github.com/tanujnotes/Supernova) alternative of Nova Launcher.

##

Thank you!

---

## ddzm — 车机 / 平板 / 学习机 / 带屏音箱桌面

本仓库是 [Olauncher](https://github.com/tanujnotes/Olauncher) v6.11.5（`2606973`）的 fork，完整上游提交历史保留在 `main`。**GPL-3.0 许可证随 fork 继承**；如需闭源商用，须改用 Apache-2.0 的实现或重写。目标形态以横屏大屏为主，竖屏手机走降级布局。

### 模块

```
app        组装层：Application 引导、WindowHost 实现、原有 Olauncher UI（逐步迁移）
  ├── card      8 张内置卡片 + CardHostView 渲染器（三区 / 拖拽固定 / 昼夜主题）
  ├── window    L0-L3 窗口策略、运行时能力探测、分屏运行时验证、shell 桥接占位
  ├── media     NotificationListenerService + MediaSession 媒体状态源（P4）
  ├── appmodel  应用列表 / 启动 / 分类
  ├── config    设备画像、主题、6 套布局、Dock 与卡片固定位置持久化
  └── core      ★ 全部 SPI、注册表、布局引擎、能力模型、降级分发
```

依赖方向严格单向：`core` 不依赖任何其他模块，`app` 依赖全部。**新增卡片或窗口策略只改 `card` / `window`（或新增模块），`core` 零改动。**

### 两个扩展点

**1. 加一张卡片**（`card` 模块或独立模块）

```kotlin
class WeatherCardFactory : CardFactory {
    override fun create(ctx: CardContext): CardView = /* 返回任意 View 的包装 */
}
scope.cards.register(
    CardDescriptor(id = "weather", kind = "weather", title = "天气",
                   requires = setOf(Capability.NOTIFICATION_LISTENER)),
    WeatherCardFactory()
)
```

`CardDescriptor.requires` 声明能力依赖，`DefaultLayoutEngine` 会按运行时 `CapabilitySnapshot` 自动过滤；布局槽位用 `acceptedKinds` 匹配 `kind`。卡片不需要、也不应该知道自己是装在哪个布局里。

**2. 加一种窗口策略**（`window` 模块）

```kotlin
class FreeformWindowStrategy : WindowStrategy {
    override val id = "window.freeform"
    override val level = WindowLevel.L2_SPLIT_SCREEN
    override val requires = setOf(Capability.SHELL_SPLIT_SCREEN)
    override fun canHandle(req: WindowRequest, caps: CapabilitySnapshot) = caps.hasAll(requires)
    override fun launch(req: WindowRequest, host: WindowHost): WindowResult = /* ... */
}
scope.windows.register(FreeformWindowStrategy())
```

`Framework.dispatch()` 按 `preferredLevel` 从高到低逐级尝试，任何一级失败自动降级，最终由 L0（桌面内卡片）兜底。策略只需返回 `WindowResult`，不参与调度逻辑。

### 能力分级（P1 真机实测：荣耀 X50 GT / Android 14 / API 34 / 无 root）

| 级别 | 能力 | 状态 |
|---|---|---|
| L0 | 桌面内自绘卡片 + 目标 App 全屏兜底 | ✅ 全设备可用，降级终点 |
| L1 | 悬浮窗卡片 + 引导第三方 App 自身悬浮窗 | ✅ **主力路径**，实现「左音乐右导航」同屏 |
| L2 | shell / Shizuku 触发系统分屏 | ⚠️ 条件增强：P4 起强制运行时验证 `splitScreenState`，命令 exit 0 不算成功；不通过即全屏回退并继续降级 |
| L3 | ActivityView / TaskView 真嵌入 | ❌ 无 root / 无系统签名不可达，仅保留 SPI |

标准 Android 下普通 APK **无法**把第三方 App 的 Activity 并排嵌进自己的界面；L1 的做法是导航走地图 App 自身悬浮窗、音乐走桌面自绘卡片（媒体会话 / 通知监听），与氢桌面一致。`SYSTEM_ACTIVITY_EMBED` 能力在运行时探测中恒不授予。

### 构建

沿用上游 Gradle 8.11.1 / AGP 8.9.1 / Kotlin 2.1.20 / JDK 17 / compileSdk 36。本机不装 JDK / Android SDK，编译与出包全部交给 GitHub Actions（`.github/workflows/android-ci.yml`）：结构自检 → 全模块 `testDebugUnitTest` → `assembleDebug` → 上传 `ddzm-debug-apk`。本机用 `platform-tools/adb.exe` 下载 artifact 并安装到已配对无线调试的真机做冒烟，见 `scripts/device-smoke.mjs`。

结构自检不依赖任何工具链，可随时运行：

```
node scripts/verify-structure.mjs
```

### P4：媒体卡片与窗口能力分级

- 音乐卡片不再占位：新增 `:media` 模块，`MediaNotificationService` 读取 `MediaSession` 的曲目 / 播放状态，卡片提供播放、暂停、上一首、下一首与打开播放器；未授予「通知使用权」时服务不绑定，卡片自动显示授权引导。契约在 `core/contract/Contracts.kt`，`:media` 与 `:card` 互不依赖。
- 导航卡片的启动动作改走 `Framework.dispatch`：L2 → L1 → L0 依运行时能力自动降级，并把实际落在哪一级显示出来；L0 兜底也会把地图 App 全屏拉起，不会出现「点了没反应」。
- L2 增加 `SplitScreenVerifier`：默认 `NEVER`，即干净设备不依赖 Shizuku / adb；只有外部注入真实 verifier 且 `dumpsys` 明确显示非零 `splitScreenState` 才算成功。依据见 `docs/DEVICE-FINDINGS.md`。
- Dock 应用可自定义：工具条「固定 Dock」多选应用写入 `ConfigStore.dockPackages`，长按 Dock 按钮取消固定。
- 详细实现与验收清单见 `docs/P4-CAPABILITIES.md`。

