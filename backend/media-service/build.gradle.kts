plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":streaming:storage"))
    api(project(":streaming:media-processing"))
    implementation(project(":shared:common"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test { useJUnitPlatform() }
