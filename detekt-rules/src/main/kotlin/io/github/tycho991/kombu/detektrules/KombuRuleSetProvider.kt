package io.github.tycho991.kombu.detektrules

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** Provides Kombu's own detekt rules under the rule set id `kombu`. */
class KombuRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("kombu")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        mapOf(RuleName("UnjustifiedSuppression") to ::UnjustifiedSuppression),
    )
}
