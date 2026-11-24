package io.github.tycho991.kombu.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** JDK that runs Gradle and the compilers (design D3). */
internal const val TOOLCHAIN_JDK = 21

/** Bytecode level of all produced classes (design D3). */
internal val TARGET_JVM: JavaVersion = JavaVersion.VERSION_17

/** Kotlin compiler settings shared by JVM and Android modules; warnings are errors (design D14). */
internal fun Project.configureKotlinCompilation() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(TARGET_JVM.toString()))
            allWarningsAsErrors.set(true)
        }
    }
}

/** Java toolchain and bytecode level for plain JVM modules. */
internal fun Project.configureJvmToolchain() {
    extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java).toolchain {
        languageVersion.set(JavaLanguageVersion.of(TOOLCHAIN_JDK))
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(TARGET_JVM.majorVersion.toInt())
    }
}
