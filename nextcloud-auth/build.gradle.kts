plugins {
    id("kombu.jvm.library")
    id("kombu.kotlin.serialization")
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp.coroutines)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.kotlinx.coroutines.test)
}
