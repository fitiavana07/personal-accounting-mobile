package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.ui.home.HomeNoteAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HomeNoteAdapterTest {

    @Test
    fun `hidden by default`() {
        val adapter = HomeNoteAdapter()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `becomes visible when set`() {
        val adapter = HomeNoteAdapter()
        adapter.setVisible(true)
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `hides again when unset`() {
        val adapter = HomeNoteAdapter()
        adapter.setVisible(true)
        adapter.setVisible(false)
        assertEquals(0, adapter.itemCount)
    }
}
