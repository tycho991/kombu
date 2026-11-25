package io.github.tycho991.kombu.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Adds kotlinx.serialization to a Kotlin module (`kombu.kotlin.serialization`, design D2 of the
 * nextcloud-login change): the compiler plugin, in the Kotlin version of the catalog, and the JSON runtime.
 */
class KotlinSerializationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", libs.library("kotlinx-serialization-json"))
        }
    }
}
