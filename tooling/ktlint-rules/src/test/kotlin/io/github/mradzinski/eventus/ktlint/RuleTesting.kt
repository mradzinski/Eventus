package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider

/** Runs one rule over [code] and returns the formatted code. */
internal fun format(code: String, rule: () -> Rule): String =
    engine(rule).format(Code.fromSnippet(code)) { AutocorrectDecision.ALLOW_AUTOCORRECT }

/** Runs one rule over [code] and returns what it reports, without changing anything. */
internal fun lint(code: String, rule: () -> Rule): List<LintError> = buildList {
    engine(rule).lint(Code.fromSnippet(code)) { add(it) }
}

private fun engine(rule: () -> Rule) = KtLintRuleEngine(ruleProviders = setOf(RuleProvider { rule() }))
