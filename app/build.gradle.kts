plugins {
    id("kombu.android.application")
    id("kombu.android.compose")
    id("kombu.kotlin.serialization")
}

dependencies {
    implementation(project(":sync"))
    implementation(project(":data"))
    // The recipe view scales ingredient lines (spec recipe-browsing, "Scaled servings").
    implementation(project(":ingredient-parser"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)

    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screen and end-to-end tests run against an in-memory recipe database.
    testImplementation(libs.androidx.room.runtime)
    // The end-to-end conflict test (A3) syncs against a Cookbook in memory.
    testImplementation(project(":test-cookbook-server"))
}
