# P4｜媒体卡片、窗口能力分级与 Dock 自定义

- 阶段：P4（分屏与悬浮应用能力分级实现）
- 前置结论：`docs/DEVICE-FINDINGS.md`（荣耀 X50 GT / Android 14 / 无 root 实测）
- 环境假设：**无 root、无系统签名、不依赖 Shizuku / adb**；高级能力缺失必须自动降级

## 1. 做了什么

| 项目 | P3 状态 | P4 状态 |
|---|---|---|
| 音乐卡片 | 权限状态占位 + 授权入口 | 真实曲目 / 歌手 / 专辑 / 播放状态，播放控制，打开播放器 |
| 导航卡片 | 直接 `startActivity` 启动地图 | 走 `Framework.dispatch` 降级链，显示实际落在哪一级 |
| L0 兜底 | `showInCard()` 恒 true、点了可能没反应 | 校验卡片已注册 + 目标 App 全屏兜底启动 |
| L2 分屏 | 命令 exit 0 即算成功 | 强制运行时 `dumpsys` 验证，不通过全屏回退；默认 verifier 为 `NEVER` |
| Dock | 自动按分类挑选，不能改 | 「固定 Dock」多选 + 长按取消，写入 `ConfigStore.dockPackages` |
| 能力刷新 | `onResume` 重探能力 | 重探能力 + 重新枚举 MediaSession |

## 2. 架构变化

### 新增 `:media` 模块

```
core/contract/Contracts.kt
  ├── MediaSnapshot        纯数据，core 不依赖 Android 图形 / 媒体 API
  ├── MediaTransport       play / pause / next / previous
  └── MediaStateSource     current / refresh / subscribe / transport

media/
  ├── MediaStateRepository      进程内状态源 + 订阅表（纯 JVM 可测）
  ├── MediaNotificationService  NotificationListenerService + MediaSessionManager
  ├── MediaModule               以 ServiceKeys.MEDIA_STATE 注册契约
  └── MediaStateRepositoryTest  5 个 JVM 单测
```

`DdzmApplication` 把 `MediaStateHolder.repository` 注册进 `scope.services`；
音乐卡片只 `find<MediaStateSource>(ServiceKeys.MEDIA_STATE)`，**不 import `:media`**。
将来换成车机厂商媒体 SDK，只需替换 `MediaModule` 注入的实现。

`MediaNotificationService` 在 app Manifest 用全限定名声明，带
`BIND_NOTIFICATION_LISTENER_SERVICE` 权限与 `NotificationListenerService` action。
用户没在系统「通知使用权」里授权时，系统根本不会绑定它，卡片自动降级到授权引导。

### 卡片启动第三方 App 的统一入口

`card/WindowLauncher.kt` 把 `LAUNCHER_SCOPE` / `WINDOW_HOST` 两个服务取出来，
交给 `Framework.dispatch`。卡片自己不判断设备能力，同一份代码在车机 / 平板 / 手机上
按运行时能力落在不同级别：

```
preferredLevel = L2_SPLIT_SCREEN
  L2  SplitScreenWindowStrategy   requires SHELL_SPLIT_SCREEN + shell.available()
                                  + command.build != null
      ├─ shell execute(cmd) exit != 0           → Failed
      ├─ 运行时 verifier 未通过                 → 全屏回退 + Failed
      └─ 通过                                  → Handled(L2)
  L1  OverlayWindowStrategy       requires OVERLAY_WINDOW + targetPackage
      └─ startApp()                            → Handled(L1)
  L0  InCardWindowStrategy        cardId 已注册 + targetPackage 全屏启动
                                                → Handled(L0)
```

`WindowResult.describe()` 把结果压成一行显示在导航卡片里，真机联调时不用看 logcat
也能知道降级到了哪一级。

### L2 的运行时验证（对 P1 乐观判断的修正）

`docs/DEVICE-FINDINGS.md` 实测：荣耀 X50 GT 的 `cmd activity start --windowingMode 3/4`
返回成功，但 `splitScreenState:0`、bounds 仍全屏、截图是单全屏应用。
因此 P4 增加 `SplitScreenVerifier` SPI：

- `SplitScreenVerifier.NEVER`：默认，恒不通过 → L2 永不假装成功，直接降级。
- `DumpsysSplitScreenVerifier`：解析 `dumpsys activity activities`，只有出现
  **非零** `splitScreenState` 才通过；字段缺失或全为 0 一律失败。
- 验证失败会调用 `host.startApp(target)` 全屏兜底，再返回 `Failed` 让框架继续降级。

`WindowModule` 暴露 `splitVerifier` / `splitCommand` 构造参数，接入 Shizuku 时
注入真实实现即可，**core 与所有窗口策略都不用改**。

## 3. 真机验收清单

| 场景 | 可在现有设备验证 | 说明 |
|---|---|---|
| 编译 / 全模块 JVM 单测 / 出包 | ✅ | GitHub Actions `android-ci.yml` |
| 三区界面 / 6 布局 / 昼夜 / 拖拽固定 | ✅ | 荣耀 X50 GT + adb 预览 flag |
| 音乐卡片显示授权引导 | ✅ | 未授权通知使用权时 |
| 音乐卡片显示真实曲目 + 播放控制 | ✅（需授权 + 装播放器） | 授权「通知使用权」后播放任意音乐 |
| 悬浮窗权限授权 → 能力刷新 | ✅ | Manifest 已声明 `SYSTEM_ALERT_WINDOW` |
| Dock 固定 / 长按取消 | ✅ | 工具条「固定 Dock」 |
| 导航按钮降级链显示 | ✅ | 无悬浮权限时应落到 L0 + 全屏启动 |
| **左音乐右导航同屏** | ❌ 需目标设备 | 需真实车机 / 平板 + **高德车机悬浮共存版**；手机版高德不画悬浮共存窗 |
| **L2 真实系统分屏** | ❌ 需 Shizuku + 兼容设备 | 默认不启用；需注入 `splitCommand` 与 verifier |
| **默认桌面 / HOME role** | ❌ 需 AOSP 类设备 | 荣耀 OEM 锁死，见 `DEVICE-FINDINGS.md` 第 1 节 |

### 关键命令

```bash
# 车机三区预览（单实例下也生效，MainActivity.onNewIntent 会 setIntent + recreate）
adb shell am start -n app.olauncher.debug/app.olauncher.MainActivity \
  --ez ddzm_car_preview true

# 通知使用权 / 悬浮窗授权入口
adb shell am start -a android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
adb shell am start -a android.settings.action.MANAGE_OVERLAY_PERMISSION \
  -d package:app.olauncher.debug

# 授权后确认能力探测结果
adb shell dumpsys activity services com.ddzm.media.MediaNotificationService
adb shell settings get secure enabled_notification_listeners
```

## 4. 已知限制

1. **不是 Activity 真嵌入**：普通 APK 无法用 `ActivityView` / `TaskView` 并排嵌入
   第三方 Activity（需系统签名 / 定制 ROM），L3 只保留 SPI。
2. **L1 依赖第三方 App 配合**：桌面本身不自绘地图，地图画面由地图 App 自身的悬浮窗提供。
3. **L2 默认关闭**：`NoopShellExecutor.available() == false` + `SplitScreenVerifier.NEVER`，
   干净设备不暴露分屏入口，也不依赖无线调试 / Shizuku。
4. **HOME 角色未验证**：荣耀 X50 GT 的 OEM 限制，需要 AOSP 类设备或 CI emulator smoke。
5. **许可证**：fork Olauncher 继承 GPL-3.0，闭源 / 商用需换 Apache-2.0 路线或重写。
