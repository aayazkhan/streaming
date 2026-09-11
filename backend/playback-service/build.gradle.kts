plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":shared:common"))
    api(project(":shared:playback"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.java.jwt)
    implementation(libs.datetime)
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test { useJUnitPlatform() }
