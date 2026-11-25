plugins {
    id("kombu.jvm.library")
    id("kombu.kotlin.serialization")
}

dependencies {
    api(project(":nextcloud-auth"))

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
