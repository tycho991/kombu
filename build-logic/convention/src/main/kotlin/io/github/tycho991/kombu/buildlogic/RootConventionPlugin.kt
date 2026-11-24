package io.github.tycho991.kombu.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Root project (`kombu.root`): checks the build scripts and build logic, aggregates the API docs. */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(this == rootProject) { "kombu.root must only be applied to the root project" }
        pluginManager.apply("base")
        configureDokkaAggregation()
        configureDetekt(
            sources =
            listOf(
                "build.gradle.kts",
                "settings.gradle.kts",
                "build-logic/settings.gradle.kts",
                "build-logic/convention/build.gradle.kts",
                "build-logic/convention/src/main/kotlin",
            ),
        )
    }
}
