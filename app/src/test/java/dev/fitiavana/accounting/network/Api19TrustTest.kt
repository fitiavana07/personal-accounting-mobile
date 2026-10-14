package dev.fitiavana.accounting.network

import org.junit.Assert.assertTrue
import org.junit.Test

class Api19TrustTest {

    private val digiCertGlobalRootG2 = "CN=DigiCert Global Root G2"

    @Test
    fun bundledRootsIncludeDigiCertGlobalRootG2() {
        val subjects = Api19Trust.bundledRoots().map { it.subjectX500Principal.name }

        assertTrue(subjects.toString(), subjects.any { it.contains(digiCertGlobalRootG2) })
    }

    @Test
    fun trustManagerAcceptsBundledRootsAlongsideSystemOnes() {
        val issuers = Api19Trust.trustManager().acceptedIssuers.map { it.subjectX500Principal.name }

        assertTrue(issuers.any { it.contains(digiCertGlobalRootG2) })
    }
}
