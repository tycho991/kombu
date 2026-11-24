package io.github.tycho991.kombu.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.util.Properties

/** Git-ignored file in the repository root that points to the release keystore (design D8). */
internal const val KEYSTORE_PROPERTIES = "keystore.properties"

private val REQUIRED_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

/**
 * Signs the release variant with the keystore named in `keystore.properties`, if that file exists.
 * Without it the release APK stays unsigned and the build says so. v1 signing is off: minSdk 29
 * only needs the v2 and v3 schemes.
 */
internal fun Project.configureReleaseSigning(android: ApplicationExtension) {
    val propertiesFile = rootProject.file(KEYSTORE_PROPERTIES)
    if (!propertiesFile.exists()) {
        logger.warn("$KEYSTORE_PROPERTIES not found: the release APK will be UNSIGNED.")
        return
    }
    val properties = Properties().apply { propertiesFile.inputStream().use { load(it) } }
    val missing = REQUIRED_KEYS.filter { properties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        throw GradleException("$KEYSTORE_PROPERTIES is missing: ${missing.joinToString()}")
    }
    val keystore = file(properties.getProperty("storeFile"))
    if (!keystore.isFile) {
        throw GradleException("Keystore from $KEYSTORE_PROPERTIES not found: $keystore")
    }
    android.signingConfigs.create("release") {
        storeFile = keystore
        storePassword = properties.getProperty("storePassword")
        keyAlias = properties.getProperty("keyAlias")
        keyPassword = properties.getProperty("keyPassword")
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
    }
    android.buildTypes.getByName("release").signingConfig = android.signingConfigs.getByName("release")
}
