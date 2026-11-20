package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

internal const val RULE_SET_ID = "eventus"
internal const val MAINTAINER = "Eventus"

/**
 * The project's ktlint rules, found by ktlint through `META-INF/services`. Disable one in `.editorconfig`
 * with `ktlint_eventus_<rule-id> = disabled`.
 */
class EventusRuleSetProvider : RuleSetProviderV3(RuleSetId(RULE_SET_ID)) {

    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        RuleProvider { BlankFirstLineInClassBodyRule() },
        RuleProvider { LocalVariableSpacingRule() },
        RuleProvider { BlankLineBeforeReturnRule() },
    )
}
