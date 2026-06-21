package dev.fitiavana.accounting.ui.instruments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Instrument

class InstrumentsAdapter(
    private val onItemClick: (Instrument) -> Unit
) : RecyclerView.Adapter<InstrumentsAdapter.ViewHolder>() {

    private var items: List<Instrument> = emptyList()

    fun submitList(list: List<Instrument>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_instrument, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val codeView: TextView = view.findViewById(R.id.text_instrument_code)
        private val typeView: TextView = view.findViewById(R.id.text_instrument_type)

        fun bind(instrument: Instrument, onClick: (Instrument) -> Unit) {
            codeView.text = instrument.code
            typeView.text = instrument.type.replaceFirstChar { it.uppercase() }
            itemView.setOnClickListener { onClick(instrument) }
        }
    }
}
