package io.github.tycho991.kombu.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.dokka.gradle.DokkaExtension

/** HTML API documentation for a pure module, with `Module.md` as its introduction (design D15). */
internal fun Project.configureDokkaModule() {
    pluginManager.apply("org.jetbrains.dokka")
    val moduleDoc = file("Module.md")
    extensions.configure<DokkaExtension> {
        moduleName.set(name)
        dokkaSourceSets.configureEach {
            if (moduleDoc.exists()) includes.from(moduleDoc)
        }
    }
}

/** Aggregates the documentation of all pure modules into one site under `build/dokka/html`. */
internal fun Project.configureDokkaAggregation() {
    pluginManager.apply("org.jetbrains.dokka")
    extensions.configure<DokkaExtension> {
        moduleName.set("Kombu")
    }
    dependencies {
        MODULE_LAYERS.filterValues { it == ModuleLayer.PURE }.keys.sorted().forEach { module ->
            add("dokka", project(":$module"))
        }
    }
}
