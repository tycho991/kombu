// Plugins are declared here without applying them, so that every module resolves
// them from the same classloader. The convention plugins in build-logic apply them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    id("kombu.root")
}
