plugins {
    id("kombu.android.application")
    id("kombu.android.compose")
}

dependencies {
    implementation(project(":sync"))
    implementation(project(":data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)

    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
