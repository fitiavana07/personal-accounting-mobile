package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncIdentityTest {
    @Test
    fun debugAndReleaseUseDifferentPortsAppIdsAndCryptoNamespaces() {
        assertNotEquals(SyncIdentity.RELEASE.discoveryPort, SyncIdentity.DEBUG.discoveryPort)
        assertNotEquals(SyncIdentity.RELEASE.appId, SyncIdentity.DEBUG.appId)
        assertNotEquals(SyncIdentity.RELEASE.cryptoNamespace, SyncIdentity.DEBUG.cryptoNamespace)
    }

    @Test
    fun releaseKeepsTheBasePortAndDebugTakesTheNextOne() {
        assertEquals(41889, SyncIdentity.RELEASE.discoveryPort)
        assertEquals(41890, SyncIdentity.DEBUG.discoveryPort)
    }

    @Test
    fun theBuildTypeSelectsTheIdentity() {
        assertEquals(SyncIdentity.DEBUG, SyncIdentity.forBuild(debug = true))
        assertEquals(SyncIdentity.RELEASE, SyncIdentity.forBuild(debug = false))
    }
}
