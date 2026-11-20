package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RETURN
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * A `return` statement is preceded by a blank line, unless it's the first statement of its block.
 * Returns inside expressions, such as `?: return` or `if (…) return`, aren't statements and are left alone.
 */
class BlankLineBeforeReturnRule :
    Rule(RuleId("$RULE_SET_ID:blank-line-before-return"), About(maintainer = MAINTAINER)),
    RuleAutocorrectApproveHandler {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != RETURN || !node.isStatement) return
        val previous = node.previousStatement() ?: return
        val lineBreak = previous.lineBreakBefore(node) ?: return

        if (lineBreak.newlineCount == 1) {
            emit(lineBreak.startOffset, "Expected a blank line before return", true)
                .ifAutocorrectAllowed { lineBreak.setNewlines(2) }
        }
    }
}
