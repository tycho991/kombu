plugins {
    id("kombu.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// The exported schema is checked in, so later changes can write tested migrations (design D4).
room {
    schemaDirectory("$projectDir/schemas")
}

// The schema test (room-testing) reads the exported schema from the assets of the Robolectric unit tests.
android {
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(project(":nextcloud-auth"))
    api(project(":cookbook-client"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
}
