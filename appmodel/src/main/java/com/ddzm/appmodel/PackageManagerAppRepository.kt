package com.ddzm.appmodel

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.ddzm.core.contract.AppInfo

/**
 * 基于 PackageManager 的应用目录实现。
 *
 * 注意：Android 11+ 需要 Manifest 中的 QUERY_ALL_PACKAGES（Olauncher 基线已声明），
 * Android 15 私密空间应用还需要 LauncherApps + ACCESS_HIDDEN_PROFILES，
 * 那部分在 P3/P4 接入，此处先保证普通可见应用可用。
 */
class PackageManagerAppRepository(private val appContext: Context) : AppRepository {

    private val packageManager: PackageManager = appContext.packageManager
    private var cache: List<AppInfo> = emptyList()

    override fun list(): List<AppInfo> {
        if (cache.isEmpty()) cache = query()
        return cache
    }

    override fun find(packageName: String): AppInfo? =
        list().firstOrNull { it.packageName == packageName }

    override fun refresh() {
        cache = query()
    }

    override fun launch(packageName: String, activityName: String?): Boolean {
        val intent: Intent = if (activityName.isNullOrBlank()) {
            packageManager.getLaunchIntentForPackage(packageName)
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

    private fun query(): List<AppInfo> {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(main, 0)
        }

        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val pkg = activity.packageName
            if (pkg == appContext.packageName) return@mapNotNull null
            AppInfo(
                packageName = pkg,
                label = info.loadLabel(packageManager).toString(),
                activityName = activity.name,
                category = categorize(pkg)
            )
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun categorize(packageName: String): String {
        val name = packageName.lowercase()
        return when {
            NAV_HINTS.any { name.contains(it) } -> AppInfo.CATEGORY_NAV
            MUSIC_HINTS.any { name.contains(it) } -> AppInfo.CATEGORY_MUSIC
            VIDEO_HINTS.any { name.contains(it) } -> AppInfo.CATEGORY_VIDEO
            else -> AppInfo.CATEGORY_OTHER
        }
    }

    companion object {
        private val NAV_HINTS = listOf("amap", "autonavi", "baidu.map", "navi", "maps", "tomtom")
        private val MUSIC_HINTS = listOf("music", "qqmusic", "netease", "kugou", "kuwo", "spotify", "radio")
        private val VIDEO_HINTS = listOf("video", "iqiyi", "youku", "bilibili", "qqlive")
    }
}
