package dev.fitiavana.accounting.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import dev.fitiavana.accounting.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val textView = TextView(requireContext())
        textView.text = getString(R.string.coming_soon)
        textView.textSize = 18f
        textView.gravity = android.view.Gravity.CENTER
        return textView
    }
}