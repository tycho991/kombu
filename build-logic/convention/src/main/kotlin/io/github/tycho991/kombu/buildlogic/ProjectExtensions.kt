package io.github.tycho991.kombu.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Base package and namespace prefix of every Kombu module. */
internal const val BASE_PACKAGE = "io.github.tycho991.kombu"

/** The shared version catalog from `gradle/libs.versions.toml`. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionInt(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/** Namespace for a module, for example `:recipe-import` becomes `io.github.tycho991.kombu.recipeimport`. */
internal val Project.kombuNamespace: String
    get() = "$BASE_PACKAGE.${name.replace("-", "")}"
