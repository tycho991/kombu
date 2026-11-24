package io.github.tycho991.kombu.lintrules

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.CURRENT_API
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KombuIssueRegistryTest {
    @Test
    fun registersHardcodedComposeText() {
        LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
        val registry = KombuIssueRegistry()
        assertTrue(registry.issues.contains(HardcodedComposeTextDetector.ISSUE))
        assertEquals(CURRENT_API, registry.api)
        assertEquals("Kombu", registry.vendor.vendorName)
    }
}
