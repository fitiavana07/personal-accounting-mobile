package dev.fitiavana.accounting.features.backup

import dev.fitiavana.accounting.features.transactions.Transaction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `exchange_rate` travels in backups and P2P sync through the shared row JSON. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class TransactionJsonTest {

    @Test
    fun `a transaction with a rate round-trips`() {
        val tx = Transaction("t1", 1L, 2L, "sell", exchangeRate = 4512.375)

        assertEquals(tx, transactionFromJson(tx.toJson()))
    }

    @Test
    fun `a transaction without a rate round-trips with a null rate`() {
        val tx = Transaction("t1", 1L, 2L, "plain")

        val json = tx.toJson()

        assertTrue(json.isNull("exchange_rate"))
        assertEquals(tx, transactionFromJson(json))
    }

    @Test
    fun `an older backup without the key reads a null rate`() {
        val json = JSONObject()
            .put("id", "t1").put("createdAt", 1L).put("transactionDatetime", 2L).put("note", "old")

        assertNull(transactionFromJson(json).exchangeRate)
    }
}
