package com.ddzm.card

import android.content.ClipData
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ddzm.core.LauncherScope
import com.ddzm.core.card.CardContext
import com.ddzm.core.card.CardTheme
import com.ddzm.core.card.CardView
import com.ddzm.core.layout.LayoutAxis
import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.LayoutPlan
import com.ddzm.core.layout.SlotBinding

/**
 * 按 [LayoutPlan] 渲染卡片的容器（P3）。
 *
 * 与 P2 相比渲染入口 `render(plan, scope)` 保持不变，但支持：
 * - SlotSpec.group / groupWeight / groupAxis 描述的任意区域划分（车机三区、上下分区、双栏…）
 * - 卡片标题栏 + 圆角卡片外观，主题由 [theme] 注入
 * - 长按标题栏拖动卡片到任意槽位完成固定，通过 [onCardPinned] 回调交给宿主持久化
 *
 * 布局目录新增一种排版时，这个类不需要改动。
 */
class CardHostView(context: Context) : FrameLayout(context) {

    /** 卡片配色，切换昼夜时由宿主设置后重新 render 即可。 */
    var theme: CardTheme = CardTheme.DARK

    /** 预览 / 设备画像覆盖时，让卡片拿到与布局解析一致的上下文。 */
    var layoutContextOverride: LayoutContext? = null

    /** slotId -> cardId，由宿主持久化到 ConfigStore。 */
    var onCardPinned: ((slotId: String, cardId: String) -> Unit)? = null

    /** 长按槽位（非标题栏）时请求取消固定。 */
    var onSlotLongPress: ((slotId: String, cardId: String?) -> Unit)? = null

    private val attached = mutableListOf<CardView>()
    private val density = resources.displayMetrics.density

    private fun dp(value: Int): Int = (density * value).toInt()

    fun render(plan: LayoutPlan, scope: LauncherScope) {
        detachAll()
        setBackgroundColor(theme.background)

        val groups = LinkedHashMap<String, MutableList<SlotBinding>>()
        for (binding in plan.bindings) {
            groups.getOrPut(binding.slot.group) { mutableListOf() }.add(binding)
        }

        val flat = groups.size == 1 && groups.containsKey("")
        val outer = LinearLayout(context).apply {
            orientation = if (plan.layout.axis == LayoutAxis.COLUMN) LinearLayout.VERTICAL
            else LinearLayout.HORIZONTAL
        }

        if (flat) {
            for (binding in plan.bindings) {
                outer.addView(
                    buildSlot(binding, scope),
                    slotParams(plan.layout.axis, binding.slot.weight)
                )
            }
        } else {
            for ((_, bindings) in groups) {
                val first = bindings.first().slot
                val groupWeight = if (first.groupWeight > 0f) first.groupWeight else 1f
                val groupView = LinearLayout(context).apply {
                    orientation = if (first.groupAxis == LayoutAxis.ROW) LinearLayout.HORIZONTAL
                    else LinearLayout.VERTICAL
                }
                for (binding in bindings) {
                    groupView.addView(
                        buildSlot(binding, scope),
                        slotParams(first.groupAxis, binding.slot.weight)
                    )
                }
                outer.addView(groupView, slotParams(plan.layout.axis, groupWeight))
            }
        }

        addView(
            outer,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun slotParams(axis: LayoutAxis, weight: Float): LinearLayout.LayoutParams {
        val w = if (weight > 0f) weight else 1f
        return if (axis == LayoutAxis.ROW) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, w)
        }.apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    private fun buildSlot(binding: SlotBinding, scope: LauncherScope): View {
        val slot = binding.slot
        val cardContext = CardContext(
            appContext = context.applicationContext,
            slot = slot,
            layoutContext = layoutContextOverride ?: scope.layoutContext(),
            services = scope.services,
            theme = theme
        )
        val cardId = binding.cardId
        val card = cardId?.let { scope.cards.create(it, cardContext) }
        val title = cardId?.let { scope.cards.descriptor(it)?.title } ?: "空槽位"

        val frame = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(theme.surface)
                setStroke(dp(1), theme.muted)
            }
        }

        val body = FrameLayout(context)
        if (card != null) {
            card.onBind(cardContext)
            attached += card
            body.addView(
                card.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            body.addView(TextView(context).apply {
                text = "拖动卡片到这里"
                textSize = 13f
                setTextColor(theme.muted)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        frame.addView(body, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply { setMargins(dp(6), dp(34), dp(6), dp(6)) })

        val header = TextView(context).apply {
            text = if (card == null) "$title · 拖入卡片" else "$title  ·  长按拖动"
            textSize = 12f
            setTextColor(theme.muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(theme.background)
        }
        frame.addView(header, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(32)
        ).apply { setMargins(dp(6), dp(2), dp(6), 0) })

        if (cardId != null) {
            header.setOnLongClickListener { v ->
                val data = ClipData.newPlainText("ddzm-card", cardId)
                v.startDragAndDrop(data, View.DragShadowBuilder(v), cardId, 0)
                true
            }
        }
        frame.setOnLongClickListener {
            onSlotLongPress?.invoke(slot.id.value, cardId)
            true
        }
        frame.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED -> v.alpha = 0.55f
                DragEvent.ACTION_DRAG_EXITED -> v.alpha = 1f
                DragEvent.ACTION_DRAG_ENDED -> v.alpha = 1f
                DragEvent.ACTION_DROP -> {
                    v.alpha = 1f
                    val dragged = event.localState as? String
                    if (!dragged.isNullOrBlank()) onCardPinned?.invoke(slot.id.value, dragged)
                }
            }
            true
        }
        return frame
    }

    private fun detachAll() {
        attached.forEach { runCatching { it.onDetach() } }
        attached.clear()
        removeAllViews()
    }
}
