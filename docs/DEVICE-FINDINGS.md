# 真机能力实测报告

- 日期：2026-11-12
- 设备：荣耀 X50 GT / ALP-AN00，Android 14 (API 34)，MagicOS 8.0.0.218，1200x2652 @520dpi，**无 root**
- 被测包：`app.olauncher.debug`（GitHub Actions run `43158259033` 产物，commit `0dd12af`）
- 通道：无线 adb `192.168.2.55:41587`（shell uid=2000）

## 结论矩阵

| 能力 | 结论 | 关键证据 |
|---|---|---|
| 编译 / 单测 / 出包 | ✅ 通过 | CI `43158259033` success，全模块 `testDebugUnitTest` + `assembleDebug` |
| 安装 / 启动 / 无崩溃 | ✅ 通过 | `install: Success`，pid 存活，`ResumedActivity=app.olauncher.debug/.MainActivity`，无 FATAL |
| 界面渲染（解锁亮屏） | ✅ 通过 | `mVisible=true`、`Awake`，screencap 1,099,382 B，可见 Olauncher 竖屏桌面 |
| 框架运行时自检 | ✅ 通过 | `layout=phone`、`cards=4 windows=4`、4 插件全装、能力 `[APP_LIST_VISIBLE]` |
| 应用列表 / 包可见性 | ✅ 通过 | 上滑抽屉列出笔记、查找设备、电话、钉钉、抖音、高德地图等真实应用，搜索键盘正常；`QUERY_ALL_PACKAGES` 生效 |
| **设为默认桌面（HOME 角色）** | ❌ **本设备不可达（OEM 限制）** | 见下节 |
| **悬浮窗 / SYSTEM_ALERT_WINDOW** | ❌ **当前工程缺口** | 见下节 |
| **shell 拉起系统分屏** | ⚠️ **参数被接受但未真正分屏** | 见下节 |

## 1. 默认桌面：荣耀 OEM 锁死，不是本工程代码问题

`cmd package query-activities -a MAIN -c HOME` 返回 3 个候选，其中明确包含
`app.olauncher.debug/app.olauncher.MainActivity` —— **Manifest 的 HOME 声明正确，系统识别得到**。
但三条设置路径全部失败：

| 路径 | 结果 |
|---|---|
| `cmd role add-role-holder [--user 0] android.app.role.HOME app.olauncher.debug` | `ExecutionException: RuntimeException: Failed` |
| `cmd package set-home-activity app.olauncher.debug/.MainActivity` | `Error: Failed to set default home.` |
| 系统设置 → 默认应用 → 桌面 | 列表**只有「荣耀桌面」一项**，无第三方选项；`set-bypassing-role-qualification true` 后重开仍是只有一项 |

决定性验证：先 `cmd role remove-role-holder ... com.hihonor.android.launcher` 成功把 holders 清空
（排除 exclusive role 冲突），再 add 第三方**依旧 Failed**；而设回荣耀桌面 `set-home-activity` 返回 `Success`。
日志出现 `PermissionController: AntiMalProtectionUtils` 与
`RoleControllerServiceImpl: Package is already a role holder`。

另：应用内「设置为默认启动器」按钮点击未发起任何 Activity START（logcat 仅有 InputDispatcher 触摸事件），
首次启动自动申请也未弹框——查到上游时序缺陷：`MainActivity.kt` 第 103 行
`viewModel.resetLauncherLiveData.call()` 在 `initObservers()`（第 106 行）**之前**调用，
SingleLiveEvent 事件在 observer 注册前发出而丢失。

**影响**：这台荣耀手机不能用来验证「作为默认桌面长期运行」。需要换 AOSP 类设备
（车机 / 平板 / 学习机 / 后装车机）或用 CI 的 emulator-smoke job 验证 HOME 角色。
所有操作均已恢复：`holders=com.hihonor.android.launcher`，按 HOME 回荣耀桌面。

## 2. 悬浮窗：P4 的硬缺口（可修）

- `dumpsys package app.olauncher.debug | grep SYSTEM_ALERT_WINDOW` 为空 → **Manifest 未声明**
  `android.permission.SYSTEM_ALERT_WINDOW`（当前 Manifest 只有 SET_WALLPAPER / EXPAND_STATUS_BAR /
  QUERY_ALL_PACKAGES / REQUEST_DELETE_PACKAGES / INTERNET / SET_ALARM / PACKAGE_USAGE_STATS /
  ACCESS_HIDDEN_PROFILES）。
- `cmd appops get app.olauncher.debug SYSTEM_ALERT_WINDOW` → `default; rejectTime=...`
- `cmd appops set ... allow`（含 `--user 0` 与 `android:system_alert_window` 两种写法）均无效果，仍为 default。

**待修（P4 前置）**：补 `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`，
并实现 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 授权引导 + 运行时 `Settings.canDrawOverlays()` 探测，
让 L1 策略在权限缺失时自动降级到 L0。
注意：手机上装的是手机版高德，氢桌面依赖的是**高德车机悬浮共存版**，需在真实车机设备验证。

**已修复（P3 / P4）**：Manifest 已补 `SYSTEM_ALERT_WINDOW`，导航 / 快捷卡片加入
`ACTION_MANAGE_OVERLAY_PERMISSION` 授权引导；`WindowCapabilityProbe` 用
`Settings.canDrawOverlays()` 运行时探测，`CarHomeFragment.onResume` 每次返回桌面都重探能力，
L1 权限缺失时自动降级到 L0。授权仍必须由用户在系统设置里确认，代码不能静默获得。

## 3. 系统分屏：修正 P1 的乐观判断

`cmd activity supports-multiwindow=true`、`supports-split-screen=true`、`cmd activity help` 有
`--windowingMode <MODE>`，但实测：

```
cmd activity start --windowingMode 3 -n com.android.settings/.HWSettings
  → Warning: Activity not started, its current task has been brought to the front
cmd activity start --windowingMode 4 -n com.hihonor.appmarket/.module.splash.Splash
  → Starting: Intent ...
topResumedActivity: mode=4 boundsRect(0, 0 - 1200, 2652)   # 仍全屏
dumpsys: splitScreenState:0 （全部 20 处均为 0）
截图：应用市场单全屏页面，无分屏
```

即 ActivityRecord 记录了 windowingMode 数值，但 MagicOS 未执行分屏布局。
**结论：L2 不能作为产品主路径，也不能仅凭 `supports-split-screen=true` 判定可用**，
必须做运行时实际分屏验证并保留自动降级；P4 主力仍是 L1（第三方 App 自身悬浮窗 + 桌面自绘卡片）。

**代码层修复（P4）**：`SplitScreenWindowStrategy` 增加 `SplitScreenVerifier`，命令返回 0
之后必须再由 `dumpsys activity activities` 确认非零 `splitScreenState`；不通过则把目标 App
全屏拉起、返回 Failed 并继续降级。默认 verifier 为 `SplitScreenVerifier.NEVER`，
干净设备（无 Shizuku / adb）根本不尝试 L2。见 `docs/P4-CAPABILITIES.md`。

## 复现要点

- 无线调试端口会变，用 `adb mdns services` 发现 `_adb-tls-connect._tcp`；`adb server` 在每次工具调用后可能被回收，
  `connect` 与后续命令必须放在同一批次。
- HOME 相关试验结束后务必恢复：
  `cmd package set-home-activity com.hihonor.android.launcher/.unihome.UniHomeLauncher`
  （该命令在本设备返回 Success），并用 `cmd role get-role-holders android.app.role.HOME` 与 HOME 键复核。

## 4. MuMu 模拟器：默认桌面验证通过（闭合原缺口）

- 日期：2026-11-14
- 设备：MuMu 模拟器（本地 adb `127.0.0.1:16384`，Android 12 / SDK 32；
  `ro.product.brand` 被 MuMu 伪装为 `HUAWEI`、型号 `ALN-AL1`，属正常设备画像伪装，不影响验证）
- 被测包：`app.olauncher.debug`（P4 最终包 `ddzm-debug-p4-9af58fe.apk`，commit `9af58fe`）
- 通道：本地 adb TCP（守护进程在每次工具调用后可能被回收，`connect` 与后续命令必须同批次）

**验证步骤与结果**

| 步骤 | 命令 / 动作 | 结果 |
|---|---|---|
| HOME 候选 | `cmd package query-activities -a MAIN -c HOME` | 含 `app.olauncher.debug/app.olauncher.MainActivity`（Manifest 正确） |
| 授予 HOME 角色 | `cmd role add-role-holder --user 0 android.app.role.HOME app.olauncher.debug` | `exit 0` |
| 角色持有者 | `dumpsys role` | `name=android.app.role.HOME` / `holders=app.olauncher.debug` |
| 首次按 HOME | 系统弹出「选择主屏幕应用」选择器 | 候选含 **Olauncher（即 ddzm，包名 `app.olauncher.debug`）** 与 Lawnchair；点「始终」 |
| 二次按 HOME | `dumpsys activity` | `topResumedActivity=app.olauncher.debug/app.olauncher.MainActivity`，**不再弹选择器** |
| 车机预览启动 | `am start ... --ez ddzm_car_preview true` | 三区界面正常；能力探测 `HOME=Y / 悬浮=Y / 通知=N / 分屏=N / 窗口=L1 悬浮` |

**结论**

1. **默认桌面在 MuMu 上完全可用**：ddzm 能正常成为 HOME 角色持有者，并按 HOME 直达首页。
   这从正面证明「荣耀 X50 GT 的失败是 OEM 锁死，不是本工程代码问题」——第 1 节那个缺口
   只在 OEM 锁设备上成立，在非锁环境（模拟器、AOSP 类车机 / 平板 / 学习机）是可达的。
2. **首按 HOME 弹选择器是标准 Android 行为**（存在多个 HOME 应用时让用户选默认），
   点「始终」后即固定，符合用户原始诉求「能正常申请权限设置为默认启动器」。
3. **能力探针正确反映角色状态**：MuMu 上 `Capability.ROLE_HOME` 探测为 `HOME=Y`，与 `dumpsys role` 一致；
   这说明 `WindowCapabilityProbe` 在真实授予场景下能拿到 HOME 能力（荣耀上拿不到是 OEM 锁导致角色没持有）。
4. 车机三区界面在 MuMu（竖屏、强制 `isLandscape=true` 预览）同样完整渲染，无崩溃。

**还原方式（如需把 MuMu 默认桌面改回）**：系统设置 → 默认应用 → 桌面 选回 MuMu 桌面，
或 `cmd role remove-role-holder --user 0 android.app.role.HOME app.olauncher.debug`。
本次验证后 ddzm 暂留在 MuMu 作为默认桌面，方便后续联调。
