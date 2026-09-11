plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":shared:playback"))
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content)
    implementation(libs.ktor.serialization.json)
    implementation(libs.datetime)
    implementation(libs.serialization.json)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test { useJUnitPlatform() }
