package dev.fitiavana.accounting.ui.transactiondetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.model.TransactionEntry
import dev.fitiavana.accounting.data.model.TransactionWithEntries
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.UiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailActivity : AppCompatActivity() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_transaction)

        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: run { finish(); return }

        val db = AppDatabase.getInstance(this)
        val transactionRepo = TransactionRepository(db.transactionDao())
        val accountRepo = AccountRepository(db.accountDao())

        Thread {
            val twe = transactionRepo.getWithEntries(transactionId)
            val accounts = accountRepo.getAll().value ?: db.accountDao().getAllSync()
            val instruments = db.instrumentDao().getAllSync().associateBy { it.code }
            val accountsMap = accounts.associate { it.id to it }
            runOnUiThread {
                if (twe != null) bindData(twe, accountsMap, instruments)
                else finish()
            }
        }.start()
    }

    private fun bindData(twe: TransactionWithEntries, accountsMap: Map<String, Account>, instruments: Map<String, Instrument>) {
        val t = twe.transaction

        setFieldValue(R.id.value_id, t.id)
        setFieldValue(R.id.value_created, dateFormat.format(Date(t.createdAt)))
        setFieldValue(R.id.value_date, dateFormat.format(Date(t.transactionDatetime)))

        val noteSection = findViewById<LinearLayout>(R.id.section_note)
        if (t.note.isBlank()) {
            noteSection.visibility = android.view.View.GONE
        } else {
            noteSection.visibility = android.view.View.VISIBLE
            setFieldValue(R.id.value_note, t.note)
        }

        val tableEntries = findViewById<TableLayout>(R.id.table_entries)
        tableEntries.removeAllViews()

        addTableHeader(tableEntries)

        var totalDebit = 0L
        var totalCredit = 0L
        for (entry in twe.entries) {
            val account = accountsMap[entry.accountId]
            val instrument = account?.instrumentCode?.let { instruments[it] }
            addEntryRow(tableEntries, account?.name ?: entry.accountId, entry, instrument, account, instruments)
            totalDebit += entry.debitAmount ?: 0L
            totalCredit += entry.creditAmount ?: 0L
        }

        addTotalsRow(tableEntries, totalDebit, totalCredit)
    }

    private fun setFieldValue(id: Int, value: String) {
        findViewById<TextView>(id).text = value
    }

    private fun addTableHeader(table: TableLayout) {
        val row = TableRow(this)
        row.addView(makeCell(getString(R.string.label_account), bold = true))
        row.addView(makeCell(getString(R.string.label_debit), bold = true, gravity = Gravity.END))
        row.addView(makeCell(getString(R.string.label_credit), bold = true, gravity = Gravity.END))
        table.addView(row)
    }

    private fun addEntryRow(table: TableLayout, accountName: String, entry: TransactionEntry, instrument: Instrument?, account: Account?, instruments: Map<String, Instrument>) {
        val row = TableRow(this)
        row.addView(makeCell(accountName))
        row.addView(makeCell(entry.debitAmount?.let { formatAmount(it) } ?: "-", gravity = Gravity.END))
        row.addView(makeCell(entry.creditAmount?.let { formatAmount(it) } ?: "-", gravity = Gravity.END))
        table.addView(row)

        if (instrument != null && (entry.instrumentDebitAmount != null || entry.instrumentCreditAmount != null)) {
            val instrRow = TableRow(this)
            instrRow.addView(makeCell("  ${instrument.code}", italic = true))
            instrRow.addView(makeCell(
                entry.instrumentDebitAmount?.let { TransactionDisplay.formatInstrumentAmount(it, instrument) } ?: "-",
                italic = true, gravity = Gravity.END
            ))
            instrRow.addView(makeCell(
                entry.instrumentCreditAmount?.let { TransactionDisplay.formatInstrumentAmount(it, instrument) } ?: "-",
                italic = true, gravity = Gravity.END
            ))
            table.addView(instrRow)
        }

        val intermediaryInstrument = account?.intermediaryInstrumentCode?.let { instruments[it] }
        if (intermediaryInstrument != null && (entry.intermediaryDebitAmount != null || entry.intermediaryCreditAmount != null)) {
            val interRow = TableRow(this)
            interRow.addView(makeCell("  ${intermediaryInstrument.code}", italic = true))
            interRow.addView(makeCell(
                entry.intermediaryDebitAmount?.let { TransactionDisplay.formatInstrumentAmount(it, intermediaryInstrument) } ?: "-",
                italic = true, gravity = Gravity.END
            ))
            interRow.addView(makeCell(
                entry.intermediaryCreditAmount?.let { TransactionDisplay.formatInstrumentAmount(it, intermediaryInstrument) } ?: "-",
                italic = true, gravity = Gravity.END
            ))
            table.addView(interRow)
        }
    }

    private fun addTotalsRow(table: TableLayout, totalDebit: Long, totalCredit: Long) {
        val row = TableRow(this)
        row.addView(makeCell(getString(R.string.label_total), bold = true))
        row.addView(makeCell(formatAmount(totalDebit), bold = true, gravity = Gravity.END))
        row.addView(makeCell(formatAmount(totalCredit), bold = true, gravity = Gravity.END))
        table.addView(row)
    }

    private fun formatAmount(amount: Long): String = String.format("%,d", amount)

    private fun makeCell(text: String, bold: Boolean = false, italic: Boolean = false, gravity: Int = Gravity.START): TextView {
        return TextView(this).apply {
            this.text = text
            this.gravity = gravity
            setPadding(8, 8, 8, 8)
            val style = when {
                bold && italic -> android.graphics.Typeface.BOLD_ITALIC
                bold -> android.graphics.Typeface.BOLD
                italic -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            }
            setTypeface(typeface, style)
            layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_TRANSACTION_ID = "extra_transaction_id"

        fun intent(context: Context, transactionId: String): Intent =
            Intent(context, TransactionDetailActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }
    }
}
