package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionWithEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDetailPresenterTest {

    private val cash = Account(id = "acc-cash", name = "Cash", type = "asset")
    private val salary = Account(id = "acc-salary", name = "Salary Revenue", type = "revenue")
    private val btcWallet = Account(
        id = "acc-btc",
        name = "Bitcoin Wallet",
        type = "asset",
        instrumentCode = "BTC",
        intermediaryInstrumentCode = "USDT"
    )
    private val btc = Instrument(code = "BTC", note = "Bitcoin", type = "crypto", decimalPlaces = 8)
    private val usdt = Instrument(code = "USDT", note = "Tether", type = "crypto", decimalPlaces = 2)

    private fun transaction(note: String = "") =
        Transaction(id = "t1", createdAt = 0L, transactionDatetime = 0L, note = note)

    @Test
    fun `totals sum debit and credit amounts across entries`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry("e1", "t1", "acc-cash", debitAmount = 100_000L, creditAmount = null),
                TransactionEntry("e2", "t1", "acc-salary", debitAmount = null, creditAmount = 100_000L)
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(cash.id to cash, salary.id to salary),
            emptyMap()
        )

        val total = view.rows.filterIsInstance<TransactionDetailRow.Total>().single()
        assertEquals(100_000L, total.debitAmount)
        assertEquals(100_000L, total.creditAmount)
        assertEquals(100_000L, view.totalAmount)
    }

    @Test
    fun `entry with no debit yields an empty debit string`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry("e1", "t1", "acc-salary", debitAmount = null, creditAmount = 50_000L)
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(salary.id to salary),
            emptyMap()
        )

        val entry = view.rows.filterIsInstance<TransactionDetailRow.Entry>().single()
        assertEquals("", entry.debitText)
        assertEquals("50,000", entry.creditText)
    }

    @Test
    fun `instrument sub-row is emitted and scaled by decimalPlaces`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry(
                    "e1", "t1", "acc-btc",
                    debitAmount = 1_250_000L, creditAmount = null,
                    instrumentDebitAmount = 250_000L, instrumentCreditAmount = null
                )
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(btcWallet.id to btcWallet),
            mapOf(btc.code to btc, usdt.code to usdt)
        )

        val subEntries = view.rows.filterIsInstance<TransactionDetailRow.SubEntry>()
        val btcRow = subEntries.single { it.label.contains("BTC") }
        assertEquals("0.0025 BTC", btcRow.debitText)
        assertEquals("", btcRow.creditText)
    }

    @Test
    fun `intermediary sub-row is emitted from intermediaryDebitAmount`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry(
                    "e1", "t1", "acc-btc",
                    debitAmount = 1_250_000L, creditAmount = null,
                    instrumentDebitAmount = 250_000L, instrumentCreditAmount = null,
                    intermediaryDebitAmount = 27_500L, intermediaryCreditAmount = null
                )
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(btcWallet.id to btcWallet),
            mapOf(btc.code to btc, usdt.code to usdt)
        )

        val subEntries = view.rows.filterIsInstance<TransactionDetailRow.SubEntry>()
        val usdtRow = subEntries.single { it.label.contains("USDT") }
        assertEquals("275.0 USDT", usdtRow.debitText)
    }

    @Test
    fun `no sub-row when account has an instrument but entry carries no instrument amount`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry(
                    "e1", "t1", "acc-btc",
                    debitAmount = 1_250_000L, creditAmount = null
                )
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(btcWallet.id to btcWallet),
            mapOf(btc.code to btc, usdt.code to usdt)
        )

        assertEquals(0, view.rows.filterIsInstance<TransactionDetailRow.SubEntry>().size)
    }

    @Test
    fun `unknown accountId falls back to the raw id`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry("e1", "t1", "acc-missing", debitAmount = 1L, creditAmount = null)
            )
        )

        val view = TransactionDetailPresenter.present(twe, emptyMap(), emptyMap())

        val entry = view.rows.filterIsInstance<TransactionDetailRow.Entry>().single()
        assertEquals("acc-missing", entry.accountName)
    }

    @Test
    fun `blank note surfaces as blank`() {
        val twe = TransactionWithEntries(transaction(note = ""), emptyList())

        val view = TransactionDetailPresenter.present(twe, emptyMap(), emptyMap())

        assertEquals("", view.note)
    }

    @Test
    fun `credit sub-row populates the credit column, not the debit column`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry(
                    "e1", "t1", "acc-btc",
                    debitAmount = null, creditAmount = 1_250_000L,
                    instrumentDebitAmount = null, instrumentCreditAmount = 250_000L
                )
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(btcWallet.id to btcWallet),
            mapOf(btc.code to btc, usdt.code to usdt)
        )

        val btcRow = view.rows.filterIsInstance<TransactionDetailRow.SubEntry>()
            .single { it.label.contains("BTC") }
        assertEquals("", btcRow.debitText)
        assertEquals("0.0025 BTC", btcRow.creditText)
    }

    @Test
    fun `multiple entries preserve stored order`() {
        val twe = TransactionWithEntries(
            transaction(),
            listOf(
                TransactionEntry("e1", "t1", "acc-cash", debitAmount = 10L, creditAmount = null),
                TransactionEntry("e2", "t1", "acc-btc", debitAmount = 20L, creditAmount = null),
                TransactionEntry("e3", "t1", "acc-salary", debitAmount = null, creditAmount = 30L)
            )
        )

        val view = TransactionDetailPresenter.present(
            twe,
            mapOf(cash.id to cash, btcWallet.id to btcWallet, salary.id to salary),
            emptyMap()
        )

        val names = view.rows.filterIsInstance<TransactionDetailRow.Entry>().map { it.accountName }
        assertEquals(listOf("Cash", "Bitcoin Wallet", "Salary Revenue"), names)
    }
}