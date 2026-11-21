package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import io.github.mradzinski.eventus.ktlint.LocalVariableSpacingRule.Companion.AFTER_DECLARATION
import io.github.mradzinski.eventus.ktlint.LocalVariableSpacingRule.Companion.AROUND_ONE_THAT_STANDS_OUT
import io.github.mradzinski.eventus.ktlint.LocalVariableSpacingRule.Companion.BETWEEN_SHORT_DECLARATIONS
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Blank lines around local variable declarations.
 *
 * A declaration is followed by a blank line when the next statement is not a declaration. Between two
 * declarations it depends on their size. Short ones stay together, with no blank line between them. One
 * that stands out is set apart by a blank line on each side: a declaration that spans several lines, one
 * whose line is wider than [WIDE_LINE_COLUMNS] columns, or one with a comment above it.
 *
 * ```
 * val routable = handlers.filter { it.isRoutable() }
 * val handlersByEvent = routable.groupBy { it.event?.displayName }
 *
 * val duplicates = handlersByEvent.values.flatMap { sameEvent ->
 *     sameEvent.drop(1)
 * }
 *
 * val missing = eventRoots.flatMap { it.leaves() }
 *
 * return duplicates + missing
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
        val hasBlankLine = lineBreak.newlineCount > 1

        val expected = when {
            !next.isLocalVariable -> Expected.BlankLine(AFTER_DECLARATION)
            node.standsOut || next.standsOut -> Expected.BlankLine(AROUND_ONE_THAT_STANDS_OUT)
            else -> Expected.NoBlankLine
        }

        when (expected) {
            is Expected.BlankLine -> if (!hasBlankLine) {
                emit(lineBreak.startOffset, expected.message, true).ifAutocorrectAllowed { lineBreak.setNewlines(2) }
            }

            is Expected.NoBlankLine -> if (hasBlankLine) {
                emit(lineBreak.startOffset, BETWEEN_SHORT_DECLARATIONS, true)
                    .ifAutocorrectAllowed { lineBreak.setNewlines(1) }
            }
        }
    }

    private sealed interface Expected {

        data class BlankLine(val message: String) : Expected

        data object NoBlankLine : Expected
    }

    internal companion object {

        /** A declaration on one line wider than this is set apart like one that spans several lines. */
        const val WIDE_LINE_COLUMNS = 100

        const val AFTER_DECLARATION = "Expected a blank line after a variable declaration"

        const val AROUND_ONE_THAT_STANDS_OUT =
            "Expected a blank line around a variable declaration that is wide, spans several lines or has a comment"

        const val BETWEEN_SHORT_DECLARATIONS = "Unexpected blank line between short variable declarations"
    }
}
