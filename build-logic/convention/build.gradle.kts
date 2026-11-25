plugins {
    `kotlin-dsl`
}

group = "io.github.tycho991.kombu.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.kover.gradle.plugin)
    compileOnly(libs.detekt.gradle.plugin)
    compileOnly(libs.dokka.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("root") {
            id = "kombu.root"
            implementationClass = "io.github.tycho991.kombu.buildlogic.RootConventionPlugin"
        }
        register("jvmLibrary") {
            id = "kombu.jvm.library"
            implementationClass = "io.github.tycho991.kombu.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "kombu.android.library"
            implementationClass = "io.github.tycho991.kombu.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "kombu.android.application"
            implementationClass = "io.github.tycho991.kombu.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "kombu.kotlin.serialization"
            implementationClass = "io.github.tycho991.kombu.buildlogic.KotlinSerializationConventionPlugin"
        }
        register("androidCompose") {
            id = "kombu.android.compose"
            implementationClass = "io.github.tycho991.kombu.buildlogic.AndroidComposeConventionPlugin"
        }
    }
}
