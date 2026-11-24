package io.github.tycho991.kombu.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin/JVM module without Android dependencies (`kombu.jvm.library`). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureJvmToolchain()
        configureKotlinCompilation()
        enforceModuleLayers()
        configureDetekt()
        configureKover()
        if (layerOf(name) == ModuleLayer.PURE) {
            // Public API of the pure modules must declare its visibility (design D14).
            extensions.configure<KotlinJvmProjectExtension> { explicitApi() }
            configureDokkaModule()
        }

        dependencies {
            add("testImplementation", platform(libs.library("junit-bom")))
            add("testImplementation", libs.library("junit-jupiter"))
            add("testRuntimeOnly", libs.library("junit-platform-launcher"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
