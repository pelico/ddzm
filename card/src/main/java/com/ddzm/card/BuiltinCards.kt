package com.ddzm.card

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.ddzm.core.ServiceKeys
import com.ddzm.core.card.CardContext
import com.ddzm.core.card.CardFactory
import com.ddzm.core.card.CardView
import com.ddzm.core.contract.AppCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P2 示例卡片：验证「新增卡片不需要改 core」。
 * 这些是最小自绘实现，P3 会替换为带布局资源、拖拽与昼夜模式的正式卡片。
 */
private fun textCard(context: CardContext, initial: String): TextView =
    TextView(context.appContext).apply {
        text = initial
        textSize = 20f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundColor(Color.parseColor("#33000000"))
        setPadding(24, 24, 24, 24)
    }

class ClockCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val root = textCard(context, "--:--")
        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                root.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                handler.postDelayed(this, 30_000L)
            }
        }
        return object : CardView {
            override val view: View = root
            override fun onBind(ctx: CardContext) {
                handler.removeCallbacks(ticker)
                ticker.run()
            }

            override fun onDetach() {
                handler.removeCallbacks(ticker)
            }
        }
    }
}

class AppShortcutCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val root = textCard(context, "应用")
        return object : CardView {
            override val view: View = root
            override fun onBind(ctx: CardContext) {
                val catalog: AppCatalog? = ctx.services.find(ServiceKeys.APP_CATALOG)
                val first = catalog?.list()?.firstOrNull()
                root.text = first?.label ?: "应用列表为空"
                root.setOnClickListener {
                    val pkg = first?.packageName ?: return@setOnClickListener
                    val intent = ctx.appContext.packageManager.getLaunchIntentForPackage(pkg)
                        ?: return@setOnClickListener
                    ctx.appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            override fun onDetach() {
                root.setOnClickListener(null)
            }
        }
    }
}

/** P4 接入 MediaSession / 通知监听后替换为真实播放控制卡片。 */
class MusicCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val root = textCard(context, "音乐 · P4 接入 MediaSession")
        return object : CardView {
            override val view: View = root
            override fun onBind(ctx: CardContext) {}
            override fun onDetach() {}
        }
    }
}

/** 导航卡片只做入口与状态提示，地图画面由第三方 App 自身悬浮窗绘制（L1）。 */
class NavCardFactory : CardFactory {
    override fun create(context: CardContext): CardView {
        val root = textCard(context, "导航 · 悬浮地图由地图 App 提供")
        return object : CardView {
            override val view: View = root
            override fun onBind(ctx: CardContext) {}
            override fun onDetach() {}
        }
    }
}
