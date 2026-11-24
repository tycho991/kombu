plugins {
    id("kombu.android.application")
    id("kombu.android.compose")
}

dependencies {
    implementation(project(":sync"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.robolectric)
}
