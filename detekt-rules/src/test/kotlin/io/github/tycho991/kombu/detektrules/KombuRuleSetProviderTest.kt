package io.github.tycho991.kombu.detektrules

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSetId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KombuRuleSetProviderTest {
    @Test
    fun `provides the kombu rule set with UnjustifiedSuppression`() {
        val ruleSet = KombuRuleSetProvider().instance()
        assertEquals(RuleSetId("kombu"), ruleSet.id)
        assertTrue(ruleSet.rules.containsKey(RuleName("UnjustifiedSuppression")))
    }
}
