package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.UiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionDetailActivity : AppCompatActivity() {

    private val dateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private val createdDateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_detail)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_transaction)

        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
            ?: run { finish(); return }

        val container = AppContainer.getInstance(this)
        val viewModel = ViewModelProvider(
            this,
            TransactionDetailViewModelFactory(
                container.transactionRepository,
                container.accountRepository,
                container.instrumentRepository
            )
        )
            .get(TransactionDetailViewModel::class.java)

        Thread {
            val detail = viewModel.loadDetail(transactionId)
            runOnUiThread {
                if (detail != null) {
                    bindData(
                        detail.transactionWithEntries.transaction,
                        TransactionDetailPresenter.present(
                            detail.transactionWithEntries,
                            detail.accountsById,
                            detail.instrumentsByCode
                        )
                    )
                } else {
                    finish()
                }
            }
        }.start()
    }

    private fun bindData(t: Transaction, view: TransactionDetailView) {
        setFieldValue(R.id.value_amount, UiUtils.formatAmountAr(this, view.totalAmount))
        setFieldValue(R.id.value_date, dateFormat.format(Date(t.transactionDatetime)))
        setFieldValue(R.id.value_id, t.id)
        setFieldValue(R.id.value_created, createdDateFormat.format(Date(t.createdAt)))

        val noteSection = findViewById<LinearLayout>(R.id.section_note)
        if (view.note.isBlank()) {
            noteSection.visibility = View.GONE
        } else {
            noteSection.visibility = View.VISIBLE
            setFieldValue(R.id.value_note, view.note)
        }

        bindEntriesTable(view.rows)
    }

    private fun bindEntriesTable(rows: List<TransactionDetailRow>) {
        val container = findViewById<LinearLayout>(R.id.table_entries)
        container.removeAllViews()

        container.addView(
            inflateRow(
                accountText = getString(R.string.label_account),
                debitText = getString(R.string.label_debit_ar),
                creditText = getString(R.string.label_credit_ar),
                backgroundColorRes = R.color.report_section_header_bg,
                textColorRes = R.color.report_header_text,
                bold = true,
                allCaps = true,
                textSizeSp = 11f
            )
        )

        for (row in rows) {
            when (row) {
                is TransactionDetailRow.Entry -> container.addView(
                    inflateRow(row.accountName, row.debitText, row.creditText, textSizeSp = 14f)
                )

                is TransactionDetailRow.SubEntry -> container.addView(
                    inflateRow(
                        row.label,
                        row.debitText,
                        row.creditText,
                        indented = true,
                        secondary = true,
                        textSizeSp = 12f
                    )
                )

                is TransactionDetailRow.Total -> {
                    container.addView(dividerRow())
                    container.addView(
                        inflateRow(
                            getString(R.string.label_total),
                            TransactionDisplay.formatAmount(row.debitAmount),
                            TransactionDisplay.formatAmount(row.creditAmount),
                            backgroundColorRes = R.color.report_grand_total_bg,
                            bold = true,
                            textSizeSp = 14f
                        )
                    )
                }
            }
        }
    }

    private fun dividerRow(): View =
        View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@TransactionDetailActivity, R.color.report_divider))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1f)
            )
        }

    private fun inflateRow(
        accountText: String,
        debitText: String,
        creditText: String,
        backgroundColorRes: Int? = null,
        textColorRes: Int? = null,
        bold: Boolean = false,
        allCaps: Boolean = false,
        indented: Boolean = false,
        secondary: Boolean = false,
        textSizeSp: Float = 15f
    ): LinearLayout {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_transaction_entry_row, null) as LinearLayout

        val accountView = row.findViewById<TextView>(R.id.text_entry_account)
        val debitView = row.findViewById<TextView>(R.id.text_entry_debit)
        val creditView = row.findViewById<TextView>(R.id.text_entry_credit)

        accountView.text = accountText
        debitView.text = debitText
        creditView.text = creditText

        if (allCaps) {
            accountView.isAllCaps = true
            debitView.isAllCaps = true
            creditView.isAllCaps = true
        }

        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        listOf(accountView, debitView, creditView).forEach {
            it.setTypeface(it.typeface, style)
            it.textSize = textSizeSp
        }

        if (indented) {
            accountView.setPadding(
                accountView.paddingStart + dpToPx(16f),
                accountView.paddingTop,
                accountView.paddingEnd,
                accountView.paddingBottom
            )
        }

        val color = when {
            textColorRes != null -> ContextCompat.getColor(this, textColorRes)
            secondary -> secondaryTextColor()
            else -> null
        }
        if (color != null) {
            accountView.setTextColor(color)
            debitView.setTextColor(color)
            creditView.setTextColor(color)
        }

        if (backgroundColorRes != null) {
            row.setBackgroundColor(ContextCompat.getColor(this, backgroundColorRes))
        }

        return row
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun secondaryTextColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun setFieldValue(id: Int, value: String) {
        findViewById<TextView>(id).text = value
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