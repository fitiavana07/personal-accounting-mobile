package dev.fitiavana.accounting.ui.home

import com.github.mikephil.charting.utils.ColorTemplate

/** Shared slice color palette for the Home tab's pie charts and the balance sheet's asset dots. */
object AssetPalette {

    private val colors = ColorTemplate.MATERIAL_COLORS.toList() +
        ColorTemplate.VORDIPLOM_COLORS.toList() +
        ColorTemplate.COLORFUL_COLORS.toList()

    fun colorFor(index: Int): Int = colors[index % colors.size]
}
