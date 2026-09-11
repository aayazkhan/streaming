plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application { mainClass.set("com.streaming.platform.mediaworker.MainKt") }

dependencies {
    implementation(project(":backend:media-service"))
    implementation(project(":streaming:media-processing"))
    implementation(project(":streaming:storage"))
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.coroutines.core)
    implementation(libs.datetime)
    implementation(libs.logback)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test { useJUnitPlatform() }
