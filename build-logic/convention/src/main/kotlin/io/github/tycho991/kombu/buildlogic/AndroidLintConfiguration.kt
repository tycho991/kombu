package io.github.tycho991.kombu.buildlogic

import com.android.build.api.dsl.Lint

/**
 * Checks whose result depends on the date of the build (a newer library or SDK was released),
 * not on the code. Dependency freshness is a deliberate decision (design D2), so these must not
 * break an otherwise unchanged build.
 */
private val TIME_DEPENDENT_CHECKS =
    setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")

/**
 * Reports lint checks bundled in third-party libraries (for example AndroidX Lifecycle) that were
 * built against an older Lint API and are therefore skipped. It is about other projects' jars, not
 * about Kombu's code; Kombu's own checks in :lint-rules are built against the same Lint version.
 */
private val THIRD_PARTY_CHECKS = setOf("ObsoleteLintCustomCheck")

/** Android Lint for every Android module: warnings are errors (design D6, D14). */
internal fun Lint.configureKombuLint() {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = true
    error += "MissingTranslation"
    disable += TIME_DEPENDENT_CHECKS
    disable += THIRD_PARTY_CHECKS
}
