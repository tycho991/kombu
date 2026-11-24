plugins { id("kombu.jvm.library") }

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)

    testImplementation(libs.lint.api)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit4)
}

// The Lint test infrastructure is built on JUnit 4 (design D11).
tasks.test {
    useJUnit()
}
