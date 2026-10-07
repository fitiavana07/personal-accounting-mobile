package dev.fitiavana.accounting.ui.backup

import dev.fitiavana.accounting.features.backup.AutoBackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class AutoBackupViewModelTest {

    private lateinit var manager: AutoBackupManager
    private lateinit var viewModel: AutoBackupViewModel

    @Before
    fun setUp() {
        manager = mock()
        viewModel = AutoBackupViewModel(manager)
    }

    @Test
    fun `status reports enabled flag, last backup time and folder`() {
        whenever(manager.isEnabled()).thenReturn(true)
        whenever(manager.lastBackupAt()).thenReturn(1_700_000_000_000L)
        whenever(manager.backupFolder()).thenReturn(File("/data/backups"))

        val status = viewModel.status()

        assertEquals(true, status.enabled)
        assertEquals(1_700_000_000_000L, status.lastBackupAt)
        assertEquals(File("/data/backups").path, status.folderPath)
    }

    @Test
    fun `a never-backed-up app has no last backup time`() {
        whenever(manager.isEnabled()).thenReturn(true)
        whenever(manager.lastBackupAt()).thenReturn(0L)
        whenever(manager.backupFolder()).thenReturn(null)

        val status = viewModel.status()

        assertNull(status.lastBackupAt)
        assertNull(status.folderPath)
    }

    @Test
    fun `setEnabled delegates to the manager`() {
        viewModel.setEnabled(false)

        verify(manager).setEnabled(false)
    }

    @Test
    fun `catch-up on start runs the backup if due`() {
        viewModel.runCatchUpSync()

        verify(manager).runIfDue()
    }
}
