package io.github.tycho991.kombu.lintrules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Reports string literals passed as visible text or accessibility descriptions to Compose
 * functions (spec localization, "No hard-coded UI text in source code"; design D7).
 */
class HardcodedComposeTextDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            val owner = method.containingClass?.qualifiedName.orEmpty()
            if (!owner.startsWith(COMPOSE_PACKAGE_PREFIX)) return
            // The source name: compiled names of functions with value-class parameters are mangled,
            // for example `Text-Nvy7gAk` for Material 3 `Text`.
            val functionName = node.methodName ?: method.name.substringBefore('-')
            val textFunction = functionName in TEXT_FUNCTIONS
            context.evaluator.computeArgumentMapping(node, method).forEach { (argument, parameter) ->
                val checked = parameter.name in TEXT_PARAMETERS || (textFunction && parameter.name == "text")
                if (checked && argument.isStringLiteral()) {
                    context.report(
                        ISSUE,
                        argument,
                        context.getLocation(argument),
                        "Hard-coded text in `$functionName(${parameter.name} = ...)`: " +
                            "load it from a string resource.",
                    )
                }
            }
        }
    }

    private fun UExpression.isStringLiteral(): Boolean = skipParenthesizedExprDown() is UInjectionHost

    /** Issue definition and the parameters the detector inspects. */
    companion object {
        private const val COMPOSE_PACKAGE_PREFIX = "androidx.compose."
        private val TEXT_FUNCTIONS = setOf("Text", "BasicText")
        private val TEXT_PARAMETERS = setOf("contentDescription", "label", "placeholder", "title")

        /** Hard-coded user-visible text in Compose code. */
        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "HardcodedComposeText",
                briefDescription = "Hard-coded text in Compose UI",
                explanation =
                """
                    User-visible text and content descriptions must come from string resources, so that \
                    every text is translated (English default, German translation).

                    This check reports a string literal or string template passed directly to the `text` \
                    parameter of `Text`/`BasicText`, or to a `contentDescription`, `label`, `placeholder` or \
                    `title` parameter of a function from an `androidx.compose` package. It does not follow \
                    variables: `val s = "x"; Text(s)` is not reported and stays a review point.
                    """,
                category = Category.I18N,
                priority = 7,
                severity = Severity.ERROR,
                implementation = Implementation(HardcodedComposeTextDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
