package com.ddzm.card

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.ddzm.core.LauncherScope
import com.ddzm.core.card.CardContext
import com.ddzm.core.card.CardView
import com.ddzm.core.layout.LayoutPlan

/**
 * 按 [LayoutPlan] 渲染卡片的容器。P2 是最小的等权重横排实现，
 * P3 会扩展成左 Dock / 中央主区 / 右侧卡片的三区布局与可拖拽槽位，
 * 但渲染入口 render(plan, scope) 保持不变。
 */
class CardHostView(context: Context) : FrameLayout(context) {

    private val attached = mutableListOf<CardView>()

    fun render(plan: LayoutPlan, scope: LauncherScope) {
        detachAll()
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        for (binding in plan.bindings) {
            val weight = if (binding.slot.weight > 0f) binding.slot.weight else 1f
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            val cardContext = CardContext(
                appContext = context.applicationContext,
                slot = binding.slot,
                layoutContext = scope.layoutContext(),
                services = scope.services
            )
            val card = binding.cardId?.let { scope.cards.create(it, cardContext) }

            if (card != null) {
                card.onBind(cardContext)
                attached += card
                row.addView(card.view, params)
            } else {
                row.addView(
                    View(context).apply { setBackgroundColor(Color.parseColor("#11000000")) },
                    params
                )
            }
        }

        addView(
            row,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun detachAll() {
        attached.forEach { it.onDetach() }
        attached.clear()
        removeAllViews()
    }
}
