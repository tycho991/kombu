package io.github.tycho991.kombu.lintrules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class HardcodedComposeTextDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = HardcodedComposeTextDetector()

    override fun getIssues(): List<Issue> = listOf(HardcodedComposeTextDetector.ISSUE)

    private val composeStubs: TestFile =
        kotlin(
            """
            package androidx.compose.material3

            fun Text(text: String, modifier: Any? = null) {}

            fun Icon(painter: Any?, contentDescription: String?) {}

            fun TextField(value: String, label: String? = null, placeholder: String? = null) {}
            """,
        ).indented()

    private val foundationStubs: TestFile =
        kotlin(
            """
            package androidx.compose.foundation.text

            fun BasicText(text: String) {}
            """,
        ).indented()

    private val resourceStubs: TestFile =
        kotlin(
            """
            package androidx.compose.ui.res

            fun stringResource(id: Int): String = ""
            """,
        ).indented()

    private fun check(source: String) = lint()
        .files(composeStubs, foundationStubs, resourceStubs, kotlin(source).indented())
        .allowMissingSdk()
        .run()

    fun testLiteralText() {
        check(
            """
            package example
            import androidx.compose.material3.Text
            fun Screen() { Text("Hello") }
            """,
        ).expectErrorCount(1).expectContains("HardcodedComposeText")
    }

    fun testLiteralTextWithMangledCompiledName() {
        // Value-class parameters (like Compose's Color) make the compiled name differ from the source name.
        val mangledStubs =
            kotlin(
                """
                package androidx.compose.material3

                @JvmInline
                value class Color(val value: Long)

                fun Text(text: String, color: Color = Color(0)) {}
                """,
            ).indented()
        lint()
            .files(
                mangledStubs,
                kotlin("package example\nimport androidx.compose.material3.Text\nfun Screen() { Text(\"Hello\") }"),
            )
            .allowMissingSdk()
            .run()
            .expectErrorCount(1)
    }

    fun testLiteralTextAsNamedArgument() {
        check(
            """
            package example
            import androidx.compose.material3.Text
            fun Screen() { Text(modifier = null, text = "Hello") }
            """,
        ).expectErrorCount(1)
    }

    fun testStringTemplate() {
        check(
            """
            package example
            import androidx.compose.material3.Text
            fun Screen(count: Int) { Text("${'$'}count recipes") }
            """,
        ).expectErrorCount(1)
    }

    fun testBasicText() {
        check(
            """
            package example
            import androidx.compose.foundation.text.BasicText
            fun Screen() { BasicText("Hello") }
            """,
        ).expectErrorCount(1)
    }

    fun testLiteralContentDescription() {
        check(
            """
            package example
            import androidx.compose.material3.Icon
            fun Screen() { Icon(null, contentDescription = "Delete") }
            """,
        ).expectErrorCount(1)
    }

    fun testLabelAndPlaceholder() {
        check(
            """
            package example
            import androidx.compose.material3.TextField
            fun Screen() { TextField("", label = "Name", placeholder = "e.g. Pasta") }
            """,
        ).expectErrorCount(2)
    }

    fun testTextFromResourceIsClean() {
        check(
            """
            package example
            import androidx.compose.material3.Text
            import androidx.compose.ui.res.stringResource
            fun Screen() { Text(stringResource(1)) }
            """,
        ).expectClean()
    }

    fun testNullContentDescriptionIsClean() {
        check(
            """
            package example
            import androidx.compose.material3.Icon
            fun Screen() { Icon(null, contentDescription = null) }
            """,
        ).expectClean()
    }

    fun testTextFieldValueIsClean() {
        check(
            """
            package example
            import androidx.compose.material3.TextField
            fun Screen() { TextField("") }
            """,
        ).expectClean()
    }

    fun testNonComposeFunctionWithTitleIsClean() {
        check(
            """
            package example
            fun notify(title: String) {}
            fun send() { notify(title = "Hello") }
            """,
        ).expectClean()
    }

    fun testUnresolvedCallIsClean() {
        lint()
            .files(composeStubs, kotlin("package example\nfun Screen() { UnknownText(\"Hello\") }"))
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectClean()
    }
}
