package app.olauncher.car

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.DdzmApplication
import app.olauncher.R
import com.ddzm.card.CardHostView
import com.ddzm.config.ConfigStore
import com.ddzm.config.HomeMode
import com.ddzm.config.LayoutCatalog
import com.ddzm.config.LauncherConfig
import com.ddzm.config.ThemeMode
import com.ddzm.core.Framework
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys
import com.ddzm.core.card.CardTheme
import com.ddzm.core.contract.AppCatalog
import com.ddzm.core.contract.AppInfo
import com.ddzm.core.layout.LayoutContext

/**
 * 车机 / 平板 / 带屏音箱的主界面（P3）。
 *
 * 顶部工具条（布局切换 / 昼夜 / 手机桌面 / 全部应用）+ 中部三区卡片 +
 * 底部 Dock。渲染完全由 LayoutCatalog 的 LayoutSpec 与 Framework.resolveLayout
 * 驱动，所以新增布局、卡片、窗口策略都不需要改这个 Fragment。
 *
 * 预览时用 deviceClass="car" 的上下文覆盖，让竖屏手机也能验证车机排版。
 */
class CarHomeFragment : Fragment() {

    private lateinit var root: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var dockBar: LinearLayout
    private lateinit var toolbarScroll: HorizontalScrollView
    private lateinit var dockScroll: HorizontalScrollView
    private lateinit var host: CardHostView

    private val scope: LauncherScope
        get() = (requireActivity().application as DdzmApplication).scope

    private val configStore: ConfigStore?
        get() = scope.services.find(ServiceKeys.CONFIG_STORE)

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (density * value).toInt()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        // 上游主题使用 translucent status + FLAG_LAYOUT_NO_LIMITS，内容从 y=0 开始；
        // 主动给根布局留出状态栏高度，避免工具栏被挖孔/状态栏遮住。
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight =
            if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else dp(24)
        root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, statusBarHeight, 0, 0)
        }
        toolbar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        dockBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(10))
        }
        // 小屏预览时按钮可能放不下，横滑而不是挤压或裁切。
        toolbarScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(toolbar)
        }
        dockScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(dockBar)
        }
        host = CardHostView(ctx)

        root.addView(toolbarScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(dockScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        return root
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回后立刻反映新授予的悬浮窗 / 通知监听能力。
        (requireActivity().application as DdzmApplication).refreshCapabilities()
        render()
    }

    private fun render() {
        val cfg = configStore?.load() ?: LauncherConfig()
        val theme = CarPalette.of(cfg.themeMode, resources.configuration.uiMode)
        val spec = LayoutCatalog.find(cfg.activeLayoutId)
        val contextOverride = LayoutContext(
            deviceClass = "car",
            isLandscape = true,
            widthDp = resources.configuration.screenWidthDp,
            heightDp = resources.configuration.screenHeightDp,
            capabilities = scope.capabilities
        )
        val plan = Framework.resolveLayout(
            scope,
            LayoutCatalog.withPinnedCards(spec, cfg.pinnedCards),
            contextOverride = contextOverride
        )

        root.setBackgroundColor(theme.background)
        toolbarScroll.setBackgroundColor(theme.background)
        dockScroll.setBackgroundColor(theme.background)
        toolbar.setBackgroundColor(theme.background)
        dockBar.setBackgroundColor(theme.background)

        host.theme = theme
        host.layoutContextOverride = contextOverride
        host.onCardPinned = { slotId, cardId ->
            configStore?.update { it.copy(pinnedCards = it.pinnedCards + (slotId to cardId)) }
            host.post { render() }
        }
        host.onSlotLongPress = { slotId, _ ->
            configStore?.update { it.copy(pinnedCards = it.pinnedCards - slotId) }
            host.post { render() }
        }
        host.render(plan, scope)

        renderToolbar(cfg, theme)
        renderDock(cfg, theme)
    }

    private fun renderToolbar(cfg: LauncherConfig, theme: CardTheme) {
        toolbar.removeAllViews()
        val spec = LayoutCatalog.find(cfg.activeLayoutId)
        toolbar.addView(TextView(requireContext()).apply {
            text = "ddzm  ·  ${spec.label}  ·  ${cfg.themeMode.id}"
            textSize = 14f
            setTextColor(theme.muted)
            minWidth = dp(150)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        toolbar.addView(button("布局", theme) {
            configStore?.update {
                it.copy(activeLayoutId = LayoutCatalog.nextCarLayout(it.activeLayoutId))
            }
            render()
        })
        toolbar.addView(button("昼夜", theme) {
            val next = when (cfg.themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            configStore?.update { it.copy(themeMode = next) }
            render()
        })
        toolbar.addView(button("手机桌面", theme) {
            configStore?.update { it.copy(homeMode = HomeMode.PHONE) }
            requireActivity().recreate()
        })
        toolbar.addView(button("全部应用", theme) { openAppDrawer() })
    }

    private fun renderDock(cfg: LauncherConfig, theme: CardTheme) {
        dockBar.removeAllViews()
        val catalog: AppCatalog? = scope.services.find(ServiceKeys.APP_CATALOG)
        val apps = catalog?.list().orEmpty()
        val pinned = cfg.dockPackages.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
        val auto = apps.sortedWith(compareBy<AppInfo> { rank(it) }.thenBy { it.label })
        val shown = (pinned + auto).distinctBy { it.packageName }.take(7)

        for (app in shown) {
            dockBar.addView(dockButton(app.label, theme) { launch(app.packageName) })
        }
        dockBar.addView(dockButton("全部应用", theme) { openAppDrawer() })
    }

    private fun button(text: String, theme: CardTheme, onClick: () -> Unit): TextView =
        TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(theme.accent)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(theme.surface)
            }
            setOnClickListener { onClick() }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(dp(6), dp(4), dp(6), dp(4)) }
        }

    private fun dockButton(text: String, theme: CardTheme, onClick: () -> Unit): TextView {
        val view = TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(theme.foreground)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(theme.surface)
                setStroke(dp(1), theme.muted)
            }
            setOnClickListener { onClick() }
        }
        return view.also {
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(6), dp(4), dp(6), dp(4))
            }
        }
    }

    private fun rank(app: AppInfo): Int = when (app.category) {
        AppInfo.CATEGORY_NAV -> 0
        AppInfo.CATEGORY_MUSIC -> 1
        AppInfo.CATEGORY_VIDEO -> 2
        else -> 3
    }

    private fun launch(packageName: String) {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openAppDrawer() {
        runCatching { findNavController().navigate(R.id.appListFragment) }
    }
}
