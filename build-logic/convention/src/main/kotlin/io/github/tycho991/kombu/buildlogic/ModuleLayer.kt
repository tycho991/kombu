package io.github.tycho991.kombu.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.withType

/** Architectural layer of a module (spec build-system, "Module dependency direction"). */
internal enum class ModuleLayer {
    PURE,
    DATA,
    SYNC,
    APP,
    TOOLING,
}

/** Every module must be listed here; an unknown module fails the build. */
internal val MODULE_LAYERS: Map<String, ModuleLayer> =
    mapOf(
        "ingredient-parser" to ModuleLayer.PURE,
        "duration-parser" to ModuleLayer.PURE,
        "recipe-import" to ModuleLayer.PURE,
        "cookbook-client" to ModuleLayer.PURE,
        "nextcloud-auth" to ModuleLayer.PURE,
        "data" to ModuleLayer.DATA,
        "sync" to ModuleLayer.SYNC,
        "app" to ModuleLayer.APP,
        "lint-rules" to ModuleLayer.TOOLING,
        "detekt-rules" to ModuleLayer.TOOLING,
    )

/** Layers each layer may depend on through regular configurations. */
private val ALLOWED_TARGETS: Map<ModuleLayer, Set<ModuleLayer>> =
    mapOf(
        ModuleLayer.PURE to setOf(ModuleLayer.PURE),
        ModuleLayer.DATA to setOf(ModuleLayer.PURE),
        ModuleLayer.SYNC to setOf(ModuleLayer.DATA, ModuleLayer.PURE),
        ModuleLayer.APP to setOf(ModuleLayer.SYNC, ModuleLayer.DATA, ModuleLayer.PURE),
        ModuleLayer.TOOLING to emptySet(),
    )

/** Configurations that load build tooling (lint checks, detekt rules) rather than product code. */
private val TOOLING_CONFIGURATIONS = setOf("lintChecks", "lintPublish", "detektPlugins")

internal fun layerOf(moduleName: String): ModuleLayer = MODULE_LAYERS[moduleName]
    ?: throw GradleException(
        "Module '$moduleName' has no layer. Add it to MODULE_LAYERS in build-logic (ModuleLayer.kt).",
    )

/** Fails the build when a project dependency of this module points against the allowed direction (design D4). */
internal fun Project.enforceModuleLayers() {
    val ownName = name
    val ownLayer = layerOf(ownName)
    configurations.configureEach {
        val configurationName = name
        dependencies.withType<ProjectDependency>().configureEach {
            val targetName = path.substringAfterLast(':')
            val targetLayer = layerOf(targetName)
            val allowed =
                if (targetName == ownName) {
                    // AGP wires test variants to their own module.
                    true
                } else if (configurationName in TOOLING_CONFIGURATIONS) {
                    targetLayer == ModuleLayer.TOOLING
                } else {
                    targetLayer in ALLOWED_TARGETS.getValue(ownLayer)
                }
            if (!allowed) {
                throw GradleException(
                    "Forbidden module dependency: '$ownName' (${ownLayer.name}) must not depend on " +
                        "'$targetName' (${targetLayer.name}) via '$configurationName'. " +
                        "Allowed: app -> sync -> data, any module -> pure modules.",
                )
            }
        }
    }
}
