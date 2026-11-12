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
  ├── card      内置卡片（时钟 / 应用 / 音乐 / 导航）与 CardHostView 渲染
  ├── window    L0-L3 窗口策略、运行时能力探测、shell 桥接占位
  ├── appmodel  应用列表 / 启动 / 分类
  ├── config    设备画像、主题、布局与卡片固定位置持久化
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
| L0 | 桌面内自绘卡片 + 快捷启动 | ✅ 全设备可用，降级终点 |
| L1 | 悬浮窗卡片 + 引导第三方 App 自身悬浮窗 | ✅ **主力路径**，实现「左音乐右导航」同屏 |
| L2 | shell / Shizuku 触发系统分屏 | ⚠️ 条件增强：设备支持分屏，但普通 APK 无 shell 身份，仅在探测到桥接能力时启用，失败自动回退 |
| L3 | ActivityView / TaskView 真嵌入 | ❌ 无 root / 无系统签名不可达，仅保留 SPI |

标准 Android 下普通 APK **无法**把第三方 App 的 Activity 并排嵌进自己的界面；L1 的做法是导航走地图 App 自身悬浮窗、音乐走桌面自绘卡片（媒体会话 / 通知监听），与氢桌面一致。`SYSTEM_ACTIVITY_EMBED` 能力在运行时探测中恒不授予。

### 构建

沿用上游 Gradle 8.11.1 / AGP 8.9.1 / Kotlin 2.1.20 / JDK 17 / compileSdk 36。P2 阶段仅建立骨架，**尚未在本机编译验证**（本机未安装 JDK 与 Android SDK，计划于 P5 统一安装并回归）。结构自检可随时运行：

```
node scripts/verify-structure.mjs
```

