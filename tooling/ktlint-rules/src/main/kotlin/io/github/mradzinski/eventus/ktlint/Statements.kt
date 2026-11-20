package io.github.mradzinski.eventus.ktlint

import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DESTRUCTURING_DECLARATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SEMICOLON
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement

/*
 * Helpers for rules about the statements of a block: a function body, a lambda, or a script.
 */

internal val ASTNode.isStatement: Boolean
    get() = treeParent?.elementType == BLOCK

/** A `val` or `var` declared in a block, including destructuring declarations. Class properties don't count. */
internal val ASTNode.isLocalVariable: Boolean
    get() = isStatement && (elementType == PROPERTY || elementType == DESTRUCTURING_DECLARATION)

/** The statement after this one in its block, skipping comments, or null when this is the last one. */
internal fun ASTNode.nextStatement(): ASTNode? = generateSequence(treeNext) { it.treeNext }
    .firstOrNull { !it.isFiller() }
    ?.takeIf { it.elementType != RBRACE }

/** The statement before this one in its block, skipping comments, or null when this is the first one. */
internal fun ASTNode.previousStatement(): ASTNode? = generateSequence(treePrev) { it.treePrev }
    .firstOrNull { !it.isFiller() }
    ?.takeIf { it.elementType != LBRACE }

/**
 * The line break that ends this statement, before [next]: the first whitespace with a newline after it.
 * Null when [next] starts on the same line.
 */
internal fun ASTNode.lineBreakBefore(next: ASTNode): ASTNode? = generateSequence(treeNext) { it.treeNext }
    .takeWhile { it != next }
    .firstOrNull { it.isWhiteSpaceWithNewline20 }

internal val ASTNode.newlineCount: Int
    get() = text.count { it == '\n' }

/** Rewrites a line break to hold [newlines] newlines, keeping the indent of the line that follows. */
internal fun ASTNode.setNewlines(newlines: Int) {
    (this as LeafPsiElement).rawReplaceWithText("\n".repeat(newlines) + text.substringAfterLast('\n'))
}

private fun ASTNode.isFiller(): Boolean = isWhiteSpace20 || isPartOfComment20 || elementType == SEMICOLON
