package io.github.tycho991.kombu.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library module (`kombu.android.library`). Kotlin support is built into AGP 9. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = kombuNamespace
            compileSdk = libs.versionInt("compileSdk")
            defaultConfig {
                minSdk = libs.versionInt("minSdk")
            }
            compileOptions {
                sourceCompatibility = TARGET_JVM
                targetCompatibility = TARGET_JVM
            }
            lint { configureKombuLint() }
            // Robolectric needs the merged Android resources in JVM unit tests.
            testOptions { unitTests.isIncludeAndroidResources = true }
        }
        configureKotlinCompilation()
        enforceModuleLayers()
        configureDetekt()
        configureKover()
        configureAndroidUnitTests()
        dependencies {
            add("testImplementation", libs.library("junit4"))
            // Kombu's own lint checks, for example HardcodedComposeText (design D7).
            add("lintChecks", project(":lint-rules"))
        }
    }
}
