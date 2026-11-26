plugins {
    id("kombu.jvm.library")
    id("kombu.kotlin.serialization")
}

dependencies {
    api(project(":nextcloud-auth"))
    // RecipeFields reads and writes recipe times as ISO 8601 durations.
    implementation(project(":duration-parser"))
    // RemoteRecipe exposes kotlinx.serialization's JsonObject in its public API.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
