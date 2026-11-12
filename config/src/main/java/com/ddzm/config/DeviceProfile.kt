package com.ddzm.config

import android.content.Context
import android.content.res.Configuration

/**
 * 设备画像。带屏智能音箱在 Android 上通常上报 UI_MODE_TYPE_APPLIANCE，
 * 车机上为 UI_MODE_TYPE_CAR；都不满足时按 smallestWidthDp 区分平板与手机。
 * 误判可在设置里用 [LauncherConfig.deviceOverride] 强制覆盖。
 */
enum class DeviceClass(val id: String) {
    PHONE("phone"),
    TABLET("tablet"),
    CAR("car"),
    SPEAKER("speaker");

    companion object {
        fun fromId(id: String): DeviceClass = values().firstOrNull { it.id == id } ?: PHONE
    }
}

data class DeviceProfile(
    val deviceClass: DeviceClass,
    val isLandscape: Boolean,
    val widthDp: Int,
    val heightDp: Int,
    val smallestWidthDp: Int
)

object DeviceProfileDetector {
    fun detect(context: Context): DeviceProfile {
        val resources = context.resources
        val config = resources.configuration
        val metrics = resources.displayMetrics
        val density = if (metrics.density > 0f) metrics.density else 1f
        val uiMode = config.uiMode and Configuration.UI_MODE_TYPE_MASK

        val deviceClass = when {
            uiMode == Configuration.UI_MODE_TYPE_CAR -> DeviceClass.CAR
            uiMode == Configuration.UI_MODE_TYPE_APPLIANCE -> DeviceClass.SPEAKER
            config.smallestScreenWidthDp >= 600 -> DeviceClass.TABLET
            else -> DeviceClass.PHONE
        }

        return DeviceProfile(
            deviceClass = deviceClass,
            isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
            widthDp = (metrics.widthPixels / density).toInt(),
            heightDp = (metrics.heightPixels / density).toInt(),
            smallestWidthDp = config.smallestScreenWidthDp
        )
    }
}
