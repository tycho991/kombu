plugins { id("kombu.android.library") }

dependencies {
    implementation(project(":data"))
    implementation(project(":cookbook-client"))
    // SyncScheduler takes a WorkManager in its public constructor.
    api(libs.androidx.work.runtime.ktx)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
}
