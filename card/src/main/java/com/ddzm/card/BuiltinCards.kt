package com.ddzm.card

import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.ddzm.core.Capability
import com.ddzm.core.ServiceKeys
import com.ddzm.core.card.CardContext
import com.ddzm.core.card.CardFactory
import com.ddzm.core.card.CardTheme
import com.ddzm.core.card.CardView
import com.ddzm.core.contract.AppCatalog
import com.ddzm.core.contract.AppInfo
import com.ddzm.core.contract.MediaSnapshot
import com.ddzm.core.contract.MediaStateSource
import com.ddzm.core.window.WindowLevel
import com.ddzm.core.window.WindowRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 所有内置卡片共用的实现壳，避免每张卡片重复写 onDetach 清理。 */
private class SimpleCardView(
    override val view: View,
    private val detachBlock: () -> Unit = {},
    private val bindBlock: (CardContext) -> Unit = {}
) : CardView {
    override fun onBind(context: CardContext) = bindBlock(context)
    override fun onDetach() = detachBlock()
}

private val CardContext.palette: CardTheme get() = theme

private fun label(ctx: CardContext, text: String, size: Float, bold: Boolean = false): TextView =
    TextView(ctx.appContext).apply {
        this.text = text
        textSize = size
        setTextColor(ctx.palette.foreground)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }

private fun hint(ctx: CardContext, text: String): TextView =
    TextView(ctx.appContext).apply {
        this.text = text
        textSize = 12f
        setTextColor(ctx.palette.muted)
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
    }

private fun column(ctx: CardContext, vararg children: View): LinearLayout =
    LinearLayout(ctx.appContext).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 16, 20, 16)
        children.forEach { addView(it) }
    }

private fun action(ctx: CardContext, text: String, onClick: (CardContext) -> Unit): TextView =
    TextView(ctx.appContext).apply {
        this.text = text
        textSize = 13f
        setTextColor(ctx.palette.accent)
        setPadding(0, 14, 0, 6)
        setOnClickListener { onClick(ctx) }
    }

private fun overlaySettings(ctx: CardContext) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${ctx.appContext.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.appContext.startActivity(intent) }
}

private fun notificationSettings(ctx: CardContext) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.appContext.startActivity(intent) }
}

class ClockCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val clock = label(context, "--:--", 26f, true).apply { maxLines = 1 }
        val date = hint(context, "").apply { maxLines = 1 }
        val root = column(context, clock, date).apply { gravity = Gravity.CENTER }
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                val now = Date()
                clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                date.text = SimpleDateFormat("MM月dd日  EEEE", Locale.getDefault()).format(now)
                handler.postDelayed(this, 20_000L)
            }
        }
        return SimpleCardView(
            root,
            bindBlock = { ctx ->
                // 按短边缩放：手机强制横屏时高度只有 ~370dp，只看宽度会把时钟撑爆。
                val shortest = minOf(ctx.layoutContext.widthDp, ctx.layoutContext.heightDp)
                clock.textSize = when {
                    shortest >= 700 -> 52f
                    shortest >= 500 -> 40f
                    else -> 24f
                }
                clock.setTextColor(ctx.palette.foreground)
                date.setTextColor(ctx.palette.muted)
                handler.removeCallbacks(ticker)
                ticker.run()
            },
            detachBlock = { handler.removeCallbacks(ticker) }
        )
    }
}

class DateCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val day = label(context, "", 26f, true)
        val full = hint(context, "")
        return SimpleCardView(column(context, day, full)) { ctx ->
            val now = Date()
            day.text = SimpleDateFormat("dd", Locale.getDefault()).format(now)
            full.text = SimpleDateFormat("yyyy / MM · EEEE", Locale.getDefault()).format(now)
            day.setTextColor(ctx.palette.foreground)
            full.setTextColor(ctx.palette.muted)
        }
    }
}

/** 应用列表卡片：直接走 core 的 AppCatalog 契约，不依赖 :appmodel 具体类型。 */
class AppListCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        // 滚动由 CardHostView 的槽位容器统一提供，这里只产出内容，避免嵌套 ScrollView。
        val list = LinearLayout(context.appContext).apply { orientation = LinearLayout.VERTICAL }
        return SimpleCardView(list, bindBlock = { ctx -> bind(ctx, list) })
    }

    private fun bind(ctx: CardContext, list: LinearLayout) {
        list.removeAllViews()
        val catalog: AppCatalog? = ctx.services.find(ServiceKeys.APP_CATALOG)
        val apps = catalog?.list().orEmpty().take(20)
        if (apps.isEmpty()) {
            list.addView(hint(ctx, "未读取到应用（检查包可见性权限）"))
            return
        }
        for (app in apps) {
            list.addView(TextView(ctx.appContext).apply {
                text = app.label
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(ctx.palette.foreground)
                setPadding(8, 14, 8, 14)
                setOnClickListener { launch(ctx, app) }
            })
        }
    }

    private fun launch(ctx: CardContext, app: AppInfo) {
        val intent = ctx.appContext.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return
        runCatching {
            ctx.appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/**
 * 音乐卡片（P4）：读取 MediaSession 的真实曲目 / 播放状态并控制播放。
 *
 * 氢桌面「左边音乐」的正确形态 —— 桌面自绘卡片，不嵌入第三方 Activity。
 * 未授予「通知使用权」时服务不会绑定，卡片自动降级为授权引导。
 */
class MusicCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val source: MediaStateSource? = context.services.find(ServiceKeys.MEDIA_STATE)
        val title = label(context, "音乐", 17f, true).apply { maxLines = 1 }
        val track = label(context, "暂无播放信息", 16f, true).apply { maxLines = 1 }
        val artist = hint(context, "").apply { maxLines = 1 }
        val extra = hint(context, "").apply { maxLines = 1 }
        val grant = action(context, "开启通知监听") { notificationSettings(it) }
        val open = action(context, "打开播放器") { ctx -> openPlayer(ctx, source) }

        val controls = LinearLayout(context.appContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val previous = action(context, "上一首") { control(source) { previous() } }
        val toggle = action(context, "播放 / 暂停") { togglePlay(source) }
        val next = action(context, "下一首") { control(source) { next() } }
        for (button in listOf(previous, toggle, next)) {
            controls.addView(
                button,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        val root = column(context, title, track, artist, extra, controls, open, grant)
        var unsubscribe: (() -> Unit)? = null

        fun applySnapshot(snapshot: MediaSnapshot, granted: Boolean) {
            track.text = if (snapshot.hasTrack) snapshot.title.ifBlank { "未知曲目" }
            else "暂无播放信息"
            artist.text = when {
                !granted -> "需要「通知使用权」才能读取播放信息"
                !snapshot.sessionActive -> "已授权 · 等待播放器建立会话"
                snapshot.artist.isBlank() -> playStateText(snapshot.isPlaying)
                else -> snapshot.artist + " · " + playStateText(snapshot.isPlaying)
            }
            extra.text = when {
                snapshot.album.isNotBlank() -> snapshot.album
                snapshot.packageName.isNotBlank() -> "来源 " + snapshot.packageName
                else -> "桌面自绘卡片 · MediaSession"
            }
            toggle.text = if (snapshot.isPlaying) "暂停" else "播放"
            val usable = snapshot.sessionActive
            previous.isEnabled = usable
            toggle.isEnabled = usable
            next.isEnabled = usable
            grant.visibility = if (granted) View.GONE else View.VISIBLE
        }

        return SimpleCardView(
            root,
            bindBlock = { ctx ->
                val granted = ctx.layoutContext.capabilities.has(Capability.NOTIFICATION_LISTENER)
                unsubscribe?.invoke()
                unsubscribe = source?.subscribe { applySnapshot(it, granted) }
                if (source == null) applySnapshot(MediaSnapshot(), granted)
                title.setTextColor(ctx.palette.foreground)
                track.setTextColor(ctx.palette.foreground)
                artist.setTextColor(ctx.palette.muted)
                extra.setTextColor(ctx.palette.muted)
                grant.setTextColor(ctx.palette.accent)
                open.setTextColor(ctx.palette.accent)
                for (button in listOf(previous, toggle, next)) {
                    button.setTextColor(ctx.palette.accent)
                }
            },
            detachBlock = {
                unsubscribe?.invoke()
                unsubscribe = null
            }
        )
    }

    private fun playStateText(playing: Boolean): String = if (playing) "正在播放" else "已暂停"

    private fun control(source: MediaStateSource?, block: MediaTransportScope.() -> Boolean) {
        val transport = source?.transport() ?: return
        if (MediaTransportScope(transport).block()) source.refresh()
    }

    private fun togglePlay(source: MediaStateSource?) {
        val transport = source?.transport() ?: return
        val playing = source.current().isPlaying
        val ok = if (playing) transport.pause() else transport.play()
        if (ok) source.refresh()
    }

    /** 让控制 lambda 直接写 next()/previous()，不必重复 transport 前缀。 */
    private class MediaTransportScope(private val t: com.ddzm.core.contract.MediaTransport) {
        fun next(): Boolean = t.next()
        fun previous(): Boolean = t.previous()
    }

    private fun openPlayer(ctx: CardContext, source: MediaStateSource?) {
        val fromSession = source?.current()?.packageName?.takeIf { it.isNotBlank() }
        val catalog: AppCatalog? = ctx.services.find(ServiceKeys.APP_CATALOG)
        val fromCatalog = catalog?.list()
            ?.firstOrNull { it.category == AppInfo.CATEGORY_MUSIC }?.packageName
        val pkg = fromSession ?: fromCatalog ?: return
        val intent = ctx.appContext.packageManager.getLaunchIntentForPackage(pkg) ?: return
        runCatching { ctx.appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * 导航卡片（P4）：地图画面仍由地图 App 自身的悬浮窗绘制（L1 氢桌面路线），
 * 但启动动作改走 Framework.dispatch 降级链，并把实际落在哪一级显示出来。
 *
 * 注意：手机上装的是手机版高德，不会画悬浮共存窗；「左音乐右导航」需要在
 * 真实车机 + 高德车机悬浮共存版上验证（docs/DEVICE-FINDINGS.md）。
 */
class NavCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val title = label(context, "导航 / 主内容区", 20f, true)
        val status = hint(context, "地图画面由地图 App 的悬浮窗提供（氢桌面路线）")
        val dispatchInfo = hint(context, "尚未启动地图").apply { maxLines = 3 }
        val overlay = action(context, "开启悬浮窗权限") { overlaySettings(it) }
        val map = action(context, "启动地图应用") { ctx -> launchMap(ctx, dispatchInfo) }
        return SimpleCardView(column(context, title, status, dispatchInfo, overlay, map)) { ctx ->
            val granted = ctx.layoutContext.capabilities.has(Capability.OVERLAY_WINDOW)
            overlay.text = if (granted) "悬浮窗已授权 · 可引导地图 App 开启悬浮共存"
            else "开启悬浮窗权限（车机地图悬浮共存前置）"
            title.setTextColor(ctx.palette.foreground)
            status.setTextColor(ctx.palette.muted)
            dispatchInfo.setTextColor(ctx.palette.muted)
            overlay.setTextColor(ctx.palette.accent)
            map.setTextColor(ctx.palette.accent)
        }
    }

    private fun launchMap(ctx: CardContext, output: TextView) {
        val catalog: AppCatalog? = ctx.services.find(ServiceKeys.APP_CATALOG)
        val apps = catalog?.list().orEmpty()
        val target = apps.firstOrNull { it.category == AppInfo.CATEGORY_NAV }
            ?: apps.firstOrNull { looksLikeMap(it.packageName) }
        if (target == null) {
            output.text = "未找到地图应用"
            return
        }
        val result = WindowLauncher.dispatch(
            ctx,
            WindowRequest(
                targetPackage = target.packageName,
                targetActivity = target.activityName,
                cardId = "nav",
                preferredLevel = WindowLevel.L2_SPLIT_SCREEN
            )
        )
        output.text = result.describe()
    }

    private fun looksLikeMap(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return listOf("amap", "autonavi", "minimap", "baidumap", "map", "navi", "here")
            .any { pkg.contains(it) }
    }
}

/** 占位卡片：演示「新增一种卡片只需一行 register，core 零改动」。 */
class WeatherCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val title = label(context, "天气", 18f, true)
        val status = hint(context, "暂无数据源 · 可由插件替换为实况天气")
        return SimpleCardView(column(context, title, status)) { ctx ->
            title.setTextColor(ctx.palette.foreground)
            status.setTextColor(ctx.palette.muted)
        }
    }
}

class QuickCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val title = label(context, "快捷入口", 18f, true)
        val settings = action(context, "系统设置") { ctx ->
            runCatching {
                ctx.appContext.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        val overlay = action(context, "悬浮窗权限") { overlaySettings(it) }
        return SimpleCardView(column(context, title, settings, overlay)) { ctx ->
            title.setTextColor(ctx.palette.foreground)
            settings.setTextColor(ctx.palette.accent)
            overlay.setTextColor(ctx.palette.accent)
        }
    }
}

/** 调试卡片：把运行时能力矩阵直接显示出来，真机联调时能一眼看出降级到了哪一级。 */
class StatusCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val title = label(context, "设备能力", 16f, true)
        val status = hint(context, "").apply { maxLines = 7 }
        return SimpleCardView(column(context, title, status)) { ctx ->
            val c = ctx.layoutContext
            val media: MediaStateSource? = ctx.services.find(ServiceKeys.MEDIA_STATE)
            val level = when {
                c.capabilities.has(Capability.SHELL_SPLIT_SCREEN) -> "L2 可用（仍需运行时验证）"
                c.capabilities.has(Capability.OVERLAY_WINDOW) -> "L1 悬浮可用"
                else -> "L0 桌面卡片"
            }
            status.text = "画像=${c.deviceClass} 横屏=${c.isLandscape} " +
                "${c.widthDp}x${c.heightDp}dp\n" +
                "能力=${c.capabilities.granted}\n" +
                "窗口=$level\n" +
                "媒体=${if (media?.current()?.sessionActive == true) "有会话" else "无会话"}"
            title.setTextColor(ctx.palette.foreground)
            status.setTextColor(ctx.palette.muted)
        }
    }
}
