package io.github.tycho991.kombu.buildlogic

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Classes produced by code generators; they do not count towards coverage (design D12). */
private val GENERATED_CLASSES =
    listOf(
        "*.BuildConfig",
        "*.R",
        "*.R$*",
        "*.ComposableSingletons*",
        "*_Factory",
        "*_Impl",
    )

/** Applies Kover with this module's threshold and wires verification into `check` (design D12). */
internal fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    val threshold = COVERAGE_THRESHOLDS.getValue(layerOf(name))
    extensions.configure<KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    classes(GENERATED_CLASSES)
                    annotatedBy("*.Generated", "androidx.compose.ui.tooling.preview.Preview")
                }
            }
            verify {
                rule("Coverage of module '$name'") {
                    minBound(threshold.lines, CoverageUnit.LINE)
                    threshold.branches?.let { minBound(it, CoverageUnit.BRANCH) }
                }
            }
        }
    }
    tasks.named("check") {
        dependsOn("koverVerify", "koverHtmlReport", "koverXmlReport")
    }
}
