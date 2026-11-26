// Test support only: a fake Cookbook server for the tests of sync and app (design D9 of recipe-browse-edit).
// Other modules may use it only through test configurations.
plugins {
    id("kombu.jvm.library")
    id("kombu.kotlin.serialization")
}

dependencies {
    // FakeCookbookServer is a mockwebserver3 Dispatcher and takes JsonObjects in its public API.
    api(libs.okhttp.mockwebserver)
    api(libs.kotlinx.serialization.json)

    testImplementation(project(":cookbook-client"))
    testImplementation(libs.okhttp)
    testImplementation(libs.kotlinx.coroutines.test)
}
