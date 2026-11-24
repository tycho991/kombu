package io.github.tycho991.kombu.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType

/** Adds Jetpack Compose to an Android module (`kombu.android.compose`). Apply after an Android plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val application = extensions.findByType<ApplicationExtension>()
        val library = extensions.findByType<LibraryExtension>()
        check(application != null || library != null) {
            "kombu.android.compose must be applied after kombu.android.application or kombu.android.library"
        }
        application?.buildFeatures?.compose = true
        library?.buildFeatures?.compose = true

        dependencies {
            val bom = platform(libs.library("compose-bom"))
            add("implementation", bom)
            add("implementation", libs.library("compose-ui"))
            add("implementation", libs.library("compose-material3"))
            add("implementation", libs.library("compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("compose-ui-tooling"))
            add("testImplementation", bom)
            add("testImplementation", libs.library("compose-ui-test-junit4"))
            add("debugImplementation", libs.library("compose-ui-test-manifest"))
        }
    }
}
