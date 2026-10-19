package dev.fitiavana.accounting

import dev.fitiavana.accounting.features.cloudbackup.SecretCipher

/** Robolectric has no AndroidKeyStore: tests that go through [AppContainer] seal secrets with this instead. */
class InMemorySecretCipher : SecretCipher {
    override fun seal(plain: String): String = "sealed:$plain"
    override fun open(sealed: String): String? = sealed.removePrefix("sealed:").takeIf { sealed.startsWith("sealed:") }
}
