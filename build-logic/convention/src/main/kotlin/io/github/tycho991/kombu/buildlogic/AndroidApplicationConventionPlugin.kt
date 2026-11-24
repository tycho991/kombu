package io.github.tycho991.kombu.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** The Android application module (`kombu.android.application`). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            namespace = BASE_PACKAGE
            compileSdk = libs.versionInt("compileSdk")
            defaultConfig {
                applicationId = BASE_PACKAGE
                minSdk = libs.versionInt("minSdk")
                targetSdk = libs.versionInt("targetSdk")
                versionCode = 1
                versionName = "0.1.0"
            }
            buildTypes {
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                }
            }
            compileOptions {
                sourceCompatibility = TARGET_JVM
                targetCompatibility = TARGET_JVM
            }
            lint { configureKombuLint() }
            // Lets Android 13+ offer a per-app language choice (spec localization).
            androidResources { generateLocaleConfig = true }
            // Robolectric needs the merged Android resources in JVM unit tests.
            testOptions { unitTests.isIncludeAndroidResources = true }
            // No encrypted dependency metadata block in the APK, so it stays acceptable for F-Droid.
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            configureReleaseSigning(this)
        }
        configureKotlinCompilation()
        enforceModuleLayers()
        configureDetekt()
        configureKover()
        configureAndroidUnitTests()
        configureForbiddenDependencyCheck()
        dependencies {
            add("testImplementation", libs.library("junit4"))
            // Kombu's own lint checks, for example HardcodedComposeText (design D7).
            add("lintChecks", project(":lint-rules"))
        }
    }
}
