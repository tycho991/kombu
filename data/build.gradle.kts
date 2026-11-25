plugins { id("kombu.android.library") }

dependencies {
    api(project(":nextcloud-auth"))
    api(project(":cookbook-client"))
    api(libs.androidx.datastore.preferences)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
