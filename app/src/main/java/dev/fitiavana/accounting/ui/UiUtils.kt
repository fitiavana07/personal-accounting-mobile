package dev.fitiavana.accounting.ui

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.fitiavana.accounting.R

class UiUtils {
    companion object {
        fun setupActionBar(
            activity: AppCompatActivity,
            displayHomeAsUp: Boolean = true
        ) {
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            activity.setSupportActionBar(toolbar)
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(
                displayHomeAsUp
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WindowCompat.setDecorFitsSystemWindows(
                    activity.window,
                    false
                )

                ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
                    val statusBar =
                        insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    view.setPadding(0, statusBar.top, 0, 0)
                    insets
                }

                val isNightMode = (activity.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

                WindowInsetsControllerCompat(
                    activity.window,
                    activity.window.decorView
                ).apply { isAppearanceLightStatusBars = !isNightMode }
            }
        }
    }
}