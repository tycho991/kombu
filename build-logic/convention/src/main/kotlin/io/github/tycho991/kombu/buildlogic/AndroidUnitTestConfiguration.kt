package io.github.tycho991.kombu.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * JVM flags Robolectric needs on JDK 17+ to emulate the file descriptors of current Android
 * versions (it reaches into `jdk.internal.access` and `java.io`).
 */
private val ROBOLECTRIC_JVM_ARGS =
    listOf(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )

/** JVM unit tests of Android modules, including Robolectric UI tests (design D11). */
internal fun Project.configureAndroidUnitTests() {
    tasks.withType<Test>().configureEach {
        jvmArgs(ROBOLECTRIC_JVM_ARGS)
    }
}
