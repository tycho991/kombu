package io.github.tycho991.kombu.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

/** Group prefixes that must never reach the app's runtime classpath (spec build-system). */
internal val FORBIDDEN_GROUP_PREFIXES = listOf("com.google.android.gms", "com.google.firebase")

/** Runtime classpaths of the app whose content is checked. */
private val CHECKED_CLASSPATHS = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")

/** Fails when a Google Play Services or Firebase artifact is on a runtime classpath (design D5). */
abstract class CheckForbiddenDependenciesTask
@Inject
constructor() : DefaultTask() {
    /** One entry per offending component: "group:name:version (via path)". */
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun check() {
        val found = violations.get().distinct()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Google Play Services and Firebase are not allowed (spec build-system):\n" +
                    found.joinToString("\n") { "  - $it" },
            )
        }
    }
}

private fun ResolvedComponentResult.findViolations(): List<String> {
    val result = mutableListOf<String>()
    val visited = mutableSetOf<ResolvedComponentResult>()

    fun visit(component: ResolvedComponentResult, path: List<String>) {
        if (!visited.add(component)) return
        val id = component.moduleVersion
        val label = id?.let { "${it.group}:${it.name}:${it.version}" } ?: component.id.displayName
        if (id != null && FORBIDDEN_GROUP_PREFIXES.any { id.group.startsWith(it) }) {
            result += "$label (via ${path.joinToString(" -> ")})"
        }
        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
            visit(it.selected, path + label)
        }
    }
    visit(this, emptyList())
    return result
}

/** Registers `checkForbiddenDependencies` and wires it into `check` and before every assembly. */
internal fun Project.configureForbiddenDependencyCheck() {
    val violations: Provider<List<String>> =
        provider { CHECKED_CLASSPATHS.map { configurations.getByName(it).incoming.resolutionResult.rootComponent } }
            .flatMap { roots ->
                objects.listProperty<String>().apply {
                    roots.forEach { root -> addAll(root.map { it.findViolations() }) }
                }
            }
    val checkTask =
        tasks.register<CheckForbiddenDependenciesTask>("checkForbiddenDependencies") {
            description = "Fails if Google Play Services or Firebase are on a runtime classpath."
            group = "verification"
            this.violations.set(violations)
        }
    tasks.named("check") { dependsOn(checkTask) }
    tasks.named("preBuild") { dependsOn(checkTask) }
}
