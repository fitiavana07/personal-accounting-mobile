package dev.fitiavana.accounting.ui.instruments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.editinstrument.EditInstrumentActivity

class InstrumentsFragment : Fragment() {

    private lateinit var viewModel: InstrumentsViewModel
    private lateinit var adapter: InstrumentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_instruments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = AppDatabase.getInstance(requireContext())
        val repository = InstrumentRepository(db.instrumentDao(), db.accountDao())
        viewModel = ViewModelProvider(this, InstrumentsViewModelFactory(repository))
            .get(InstrumentsViewModel::class.java)

        adapter = InstrumentsAdapter { instrument ->
            startActivity(EditInstrumentActivity.editIntent(requireContext(), instrument.code))
        }
        view.findViewById<RecyclerView>(R.id.recycler_instruments).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InstrumentsFragment.adapter
        }

        viewModel.instruments.observe(viewLifecycleOwner) { instruments ->
            adapter.submitList(instruments)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_instruments, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_instrument) {
            startActivity(EditInstrumentActivity.addIntent(requireContext()))
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
