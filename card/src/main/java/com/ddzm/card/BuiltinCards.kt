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

/** 音乐卡片：P3 显示状态与授权入口，P4 接 MediaSession / 通知监听。 */
class MusicCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val title = label(context, "音乐", 18f, true)
        val status = hint(context, "")
        val action = action(context, "开启通知监听") { notificationSettings(it) }
        return SimpleCardView(column(context, title, status, action)) { ctx ->
            val granted = ctx.layoutContext.capabilities.has(Capability.NOTIFICATION_LISTENER)
            status.text = if (granted) "已授权 · 等待播放信息（P4 接入）"
            else "未授权通知监听 · 授权后可读取播放信息"
            title.setTextColor(ctx.palette.foreground)
            status.setTextColor(ctx.palette.muted)
            action.setTextColor(ctx.palette.accent)
        }
    }
}

/** 导航卡片：地图画面由第三方 App 自身悬浮窗绘制（L1），这里只做入口与权限引导。 */
class NavCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val title = label(context, "导航 / 主内容区", 20f, true)
        val status = hint(context, "地图画面由地图 App 的悬浮窗提供（氢桌面路线）")
        val overlay = action(context, "开启悬浮窗权限") { overlaySettings(it) }
        val map = action(context, "启动地图应用") { ctx -> launchMap(ctx) }
        return SimpleCardView(column(context, title, status, overlay, map)) { ctx ->
            val granted = ctx.layoutContext.capabilities.has(Capability.OVERLAY_WINDOW)
            overlay.text = if (granted) "悬浮窗已授权 · 可引导地图 App 开启悬浮共存"
            else "开启悬浮窗权限（车机地图悬浮共存前置）"
            title.setTextColor(ctx.palette.foreground)
            status.setTextColor(ctx.palette.muted)
            overlay.setTextColor(ctx.palette.accent)
            map.setTextColor(ctx.palette.accent)
        }
    }

    private fun launchMap(ctx: CardContext) {
        val catalog: AppCatalog? = ctx.services.find(ServiceKeys.APP_CATALOG)
        val target = catalog?.list()?.firstOrNull { it.category == AppInfo.CATEGORY_NAV }
            ?: return
        val intent = ctx.appContext.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: return
        runCatching {
            ctx.appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
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
        val status = hint(context, "")
        return SimpleCardView(column(context, title, status)) { ctx ->
            val c = ctx.layoutContext
            status.text = "画像=${c.deviceClass} 横屏=${c.isLandscape} " +
                "${c.widthDp}x${c.heightDp}dp\n能力=${c.capabilities.granted}"
            title.setTextColor(ctx.palette.foreground)
            status.setTextColor(ctx.palette.muted)
        }
    }
}
