package com.ddzm.window

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.ddzm.core.Capability
import com.ddzm.core.CapabilityProbe
import com.ddzm.core.CapabilitySnapshot
import com.ddzm.core.contract.ShellExecutor

/**
 * 运行时能力探测。所有结论来自设备当前状态，不来自 Build 常量推测。
 * 应用从后台恢复、权限被撤销、Shizuku 重启失效后都应重新调用 [probe]。
 */
class WindowCapabilityProbe(
    private val appContext: Context,
    private val shell: ShellExecutor
) : CapabilityProbe {

    override fun probe(): CapabilitySnapshot {
        val granted = mutableSetOf(Capability.APP_LIST_VISIBLE)
        val packageManager = appContext.packageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = appContext.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true) {
                granted += Capability.ROLE_HOME
            }
        }

        if (Settings.canDrawOverlays(appContext)) {
            granted += Capability.OVERLAY_WINDOW
        }

        val listeners = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
        )
        if (listeners != null && listeners.contains(appContext.packageName)) {
            granted += Capability.NOTIFICATION_LISTENER
            granted += Capability.MEDIA_SESSION
        }

        // P1 实测设备 supports-split-screen=true，但普通 APK 无 shell 身份，
        // 因此只有外部桥接确实可用时才授予 L2 能力。
        if (shell.available()) {
            granted += Capability.SHELL_SPLIT_SCREEN
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            granted += Capability.SYSTEM_FREEFORM
        }

        // Android 15 私密空间：声明权限只是前提，实际列表仍需 LauncherApps 处理（P3/P4）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            granted += Capability.HIDDEN_PROFILES
        }

        // SYSTEM_ACTIVITY_EMBED 需要系统签名或定制 ROM，此处永不授予。
        return CapabilitySnapshot(
            granted = granted,
            probedAtMillis = System.currentTimeMillis(),
            source = "runtime-probe"
        )
    }
}
