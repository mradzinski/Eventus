package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_BODY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RBRACE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * The body of a class starts with a blank line, so its first member does not sit right under the header:
 * ```
 * class TaskRepository(
 *     private val connectivity: Connectivity,
 * ) : Repository {
 *
 *     private val tasks = MutableStateFlow(emptyList<Task>())
 * }
 * ```
 * It applies to every body written with braces after a class-like header: classes, interfaces, objects,
 * companion objects, enum classes and anonymous objects.
 *
 * Two kinds of body are left alone: an empty one, and one whose only content is a single member that
 * fits on one line. A blank line there would only make a small class taller.
 */
class BlankFirstLineInClassBodyRule :
    Rule(RuleId("$RULE_SET_ID:blank-first-line-in-class-body"), About(maintainer = MAINTAINER)),
    RuleAutocorrectApproveHandler {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != CLASS_BODY) return

        val opening = node.firstChildNode?.takeIf { it.elementType == LBRACE } ?: return
        val lineBreak = opening.treeNext?.takeIf { it.isWhiteSpace20 } ?: return
        val content = node.content()

        if (content.isEmpty() || content.isOneShortMember()) return

        if (lineBreak.newlineCount == 1) {
            emit(lineBreak.startOffset, "Expected a blank line at the start of the class body", true)
                .ifAutocorrectAllowed { lineBreak.setNewlines(2) }
        }
    }

    /** Everything between the braces of this body except the whitespace: members and comments. */
    private fun ASTNode.content(): List<ASTNode> = generateSequence(firstChildNode) { it.treeNext }
        .filterNot { it.isWhiteSpace20 || it.elementType == LBRACE || it.elementType == RBRACE }
        .toList()

    private fun List<ASTNode>.isOneShortMember(): Boolean = size == 1 && '\n' !in single().text
}
