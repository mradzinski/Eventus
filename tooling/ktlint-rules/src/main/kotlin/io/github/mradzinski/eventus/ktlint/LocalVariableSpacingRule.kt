package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * A local variable declaration is followed by a blank line, unless the next statement is another variable
 * declaration: consecutive declarations stay together with no blank line between them.
 *
 * ```
 * val resolved = resolveService(service) ?: return@collect
 * val address = resolved.host ?: return@collect
 *
 * resolvedAddresses[normalize(resolved.serviceName)] = address
 * ```
 */
class LocalVariableSpacingRule :
    Rule(RuleId("$RULE_SET_ID:local-variable-spacing"), About(maintainer = MAINTAINER)),
    RuleAutocorrectApproveHandler {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (!node.isLocalVariable) return
        val next = node.nextStatement() ?: return
        val lineBreak = node.lineBreakBefore(next) ?: return

        if (next.isLocalVariable) {
            if (lineBreak.newlineCount > 1) {
                emit(lineBreak.startOffset, "Unexpected blank line between variable declarations", true)
                    .ifAutocorrectAllowed { lineBreak.setNewlines(1) }
            }
        } else if (lineBreak.newlineCount == 1) {
            emit(lineBreak.startOffset, "Expected a blank line after a variable declaration", true)
                .ifAutocorrectAllowed { lineBreak.setNewlines(2) }
        }
    }
}
