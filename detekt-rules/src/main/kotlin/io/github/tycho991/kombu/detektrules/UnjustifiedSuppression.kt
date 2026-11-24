package io.github.tycho991.kombu.detektrules

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Reports `@Suppress` and `@SuppressWarnings` annotations that carry no justifying comment.
 *
 * A suppression is justified by a line or block comment (not KDoc) either at the end of the
 * annotation's line or directly above the annotation, without a blank line in between.
 */
class UnjustifiedSuppression(config: Config) :
    Rule(config, "A suppression must state its reason in a comment on the same or the preceding line.") {
    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        val name = annotationEntry.shortName?.asString()
        val isSuppression = name in SUPPRESSING_ANNOTATIONS
        if (isSuppression && !hasCommentOnSameLine(annotationEntry) && !hasCommentDirectlyAbove(annotationEntry)) {
            report(
                Finding(
                    Entity.from(annotationEntry),
                    "@$name needs a comment on the same or the preceding line that explains why it is needed.",
                ),
            )
        }
    }

    private fun hasCommentOnSameLine(annotationEntry: KtAnnotationEntry): Boolean {
        var leaf: PsiElement? = PsiTreeUtil.nextLeaf(PsiTreeUtil.lastChild(annotationEntry))
        while (leaf != null && !(leaf is PsiWhiteSpace && leaf.textContains('\n'))) {
            if (leaf.isJustifyingComment()) return true
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        return false
    }

    private fun hasCommentDirectlyAbove(annotationEntry: KtAnnotationEntry): Boolean {
        var leaf: PsiElement? = PsiTreeUtil.prevLeaf(PsiTreeUtil.firstChild(annotationEntry))
        while (leaf is PsiWhiteSpace) {
            if (leaf.text.count { it == '\n' } > 1) return false
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return leaf?.isJustifyingComment() == true
    }

    private fun PsiElement.isJustifyingComment(): Boolean =
        this is PsiComment && this !is KDoc && PsiTreeUtil.getParentOfType(this, KDoc::class.java) == null

    private companion object {
        val SUPPRESSING_ANNOTATIONS = setOf("Suppress", "SuppressWarnings")
    }
}
