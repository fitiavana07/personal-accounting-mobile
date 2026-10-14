package dev.fitiavana.accounting.features.sync

import org.mockito.kotlin.mock

/**
 * A tracker over a mocked meta DAO that runs transaction blocks inline, for repository tests that
 * mock their own DAOs and only care that the write itself happens.
 */
fun inlineTracker(): ChangeTracker =
    ChangeTracker(mock(), StampClock("test", { 1L }), runInTransaction = { it.run() })
