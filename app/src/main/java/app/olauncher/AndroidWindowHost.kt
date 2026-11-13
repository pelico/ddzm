package app.olauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.ddzm.core.LauncherScope
import com.ddzm.core.window.WindowHost

/**
 * WindowHost 的 Android 实现：窗口策略与平台之间的唯一出口。
 * P4 接入 Shizuku 时只需替换这里的部分实现（或注入另一个 ShellExecutor），
 * core 与 :window 的策略代码不受影响。
 */
class AndroidWindowHost(
    private val appContext: Context,
    private val scope: LauncherScope? = null
) : WindowHost {

    override fun startApp(packageName: String, activityName: String?): Boolean {
        val intent: Intent = if (activityName.isNullOrBlank()) {
            appContext.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(packageName, activityName)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        } ?: return false

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * P4：L0 兜底不再是恒 true，而是校验卡片确实已注册，未注册就继续降级。
     * 没有 scope（例如纯 JVM 用例）时保持兼容，始终允许。
     */
    override fun showInCard(cardId: String): Boolean =
        scope?.cards?.descriptor(cardId) != null || scope == null

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    /**
     * 只是「系统准入」，**不代表这台设备真的能分屏**。
     * docs/DEVICE-FINDINGS.md 实测：荣耀 X50 GT 返回 supports-split-screen=true，
     * 但 `--windowingMode 3/4` 之后 splitScreenState 仍为 0、bounds 仍全屏。
     * 真实判定由 SplitScreenWindowStrategy 的运行时 verifier 完成。
     */
    override fun hasSplitScreenSupport(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_RAM_LOW)
}
