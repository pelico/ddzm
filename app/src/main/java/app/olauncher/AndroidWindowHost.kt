package app.olauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.ddzm.core.window.WindowHost

/**
 * WindowHost 的 Android 实现：窗口策略与平台之间的唯一出口。
 * P4 接入 Shizuku 时只需替换这里的部分实现（或注入另一个 ShellExecutor），
 * core 与 :window 的策略代码不受影响。
 */
class AndroidWindowHost(private val appContext: Context) : WindowHost {

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

    /** P3 接入 CardHostView 后改为真实展示；P2 恒返回 true 保证 L0 兜底可用。 */
    override fun showInCard(cardId: String): Boolean = true

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    override fun hasSplitScreenSupport(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_RAM_LOW)
}
