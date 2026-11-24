package io.github.tycho991.kombu.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

private const val DETEKT_RULES_PATH = ":detekt-rules"

/**
 * Static analysis and formatting (ktlint rules) with zero findings and no baseline (design D13).
 * `detektFormat` applies the auto-correctable formatting fixes.
 */
internal fun Project.configureDetekt(
    sources: List<String> =
        listOf("src/main/kotlin", "src/test/kotlin", "src/debug/kotlin", "src/release/kotlin", "build.gradle.kts"),
) {
    pluginManager.apply("dev.detekt")
    val configFiles =
        buildList {
            add(rootProject.file("config/detekt/detekt.yml"))
            if (MODULE_LAYERS[name] == ModuleLayer.PURE) {
                add(rootProject.file("config/detekt/detekt-public-api.yml"))
            }
        }
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        failOnSeverity.set(FailOnSeverity.Info)
        config.setFrom(configFiles)
        source.setFrom(sources)
    }
    dependencies {
        add("detektPlugins", libs.library("detekt-rules-ktlint-wrapper"))
        // Kombu's own rules, for example UnjustifiedSuppression. :detekt-rules checks itself
        // with its own jar, which is no cycle: detekt only depends on the compiled jar.
        add("detektPlugins", project(DETEKT_RULES_PATH))
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget.set(TARGET_JVM.toString())
        reports {
            html.required.set(true)
            checkstyle.required.set(true)
            sarif.required.set(false)
            markdown.required.set(false)
        }
    }
    val detektSource = extensions.getByType<DetektExtension>().source
    tasks.register<Detekt>("detektFormat") {
        description = "Fixes auto-correctable formatting findings."
        group = "formatting"
        autoCorrect.set(true)
        // Remaining findings are reported by `detekt`; this task only fixes what it can.
        ignoreFailures.set(true)
        buildUponDefaultConfig.set(true)
        config.setFrom(configFiles)
        setSource(detektSource)
        jvmTarget.set(TARGET_JVM.toString())
    }
}
