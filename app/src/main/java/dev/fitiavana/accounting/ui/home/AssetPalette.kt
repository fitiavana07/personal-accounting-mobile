package dev.fitiavana.accounting.ui.home

import com.github.mikephil.charting.utils.ColorTemplate

/** Shared slice colors so the assets pie chart and the balance sheet's asset dots always match. */
object AssetPalette {

    private val colors = ColorTemplate.MATERIAL_COLORS.toList() +
        ColorTemplate.VORDIPLOM_COLORS.toList() +
        ColorTemplate.COLORFUL_COLORS.toList()

    fun colorFor(index: Int): Int = colors[index % colors.size]
}
