package app.olauncher.car

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import app.olauncher.DdzmApplication
import app.olauncher.R
import com.ddzm.config.ConfigStore
import com.ddzm.config.DeviceClass
import com.ddzm.config.DeviceProfileDetector
import com.ddzm.config.HomeMode
import com.ddzm.config.LauncherConfig
import com.ddzm.config.ThemeMode
import com.ddzm.core.ServiceKeys
import com.ddzm.core.card.CardTheme

/**
 * 主界面路由。
 *
 * 三区桌面与上游 Olauncher 竖屏桌面共用同一个 HOME Activity（MainActivity），
 * 只在启动时换 NavGraph 的 startDestination，因此不会破坏已验证的手机路径，
 * 也不会出现两个 HOME 组件让系统弹选择框。
 *
 * 判定优先级：adb 预览 > 用户强制 > AUTO（设备画像 + 资源限定符）。
 * 预览通道：`adb shell am start -n <pkg>/app.olauncher.MainActivity --ez ddzm_car_preview true`
 */
object CarModeRouter {

    const val EXTRA_CAR_PREVIEW = "ddzm_car_preview"

    fun useCarHome(context: Context, intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(EXTRA_CAR_PREVIEW, false) == true) return true
        val app = context.applicationContext as? DdzmApplication ?: return false
        val config = app.scope.services.find<ConfigStore>(ServiceKeys.CONFIG_STORE)?.load()
            ?: LauncherConfig()

        return when (config.homeMode) {
            HomeMode.CAR -> true
            HomeMode.PHONE -> false
            HomeMode.AUTO -> {
                val profile = DeviceProfileDetector.detect(context)
                profile.smallestWidthDp >= 600 ||
                    profile.deviceClass == DeviceClass.CAR ||
                    profile.deviceClass == DeviceClass.SPEAKER ||
                    context.resources.getBoolean(R.bool.ddzm_default_car_home)
            }
        }
    }
}

/** 昼夜配色。不调用 AppCompatDelegate.recreate()，切换主题时只重绘卡片，避免桌面闪屏。 */
object CarPalette {
    fun of(mode: ThemeMode, uiMode: Int): CardTheme = when (mode) {
        ThemeMode.LIGHT -> CardTheme.LIGHT
        ThemeMode.DARK -> CardTheme.DARK
        ThemeMode.SYSTEM -> {
            val night = (uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            if (night) CardTheme.DARK else CardTheme.LIGHT
        }
    }
}
