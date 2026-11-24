package io.github.tycho991.kombu.lintrules

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/** Registers Kombu's own Android Lint checks. */
class KombuIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(HardcodedComposeTextDetector.ISSUE)

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(vendorName = "Kombu", feedbackUrl = "https://github.com/tycho991/kombu/issues")
}
