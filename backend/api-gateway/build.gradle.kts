plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("com.streaming.platform.gateway.MainKt")
}

dependencies {
    implementation(project(":backend:identity-service"))
    implementation(project(":backend:profile-service"))
    implementation(project(":backend:content-service"))
    implementation(project(":backend:search-service"))
    implementation(project(":backend:playback-service"))
    implementation(project(":backend:watchlist-service"))
    implementation(project(":backend:media-service"))
    implementation(project(":streaming:storage"))
    implementation(project(":shared:common"))
    implementation(project(":shared:core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content)
    implementation(libs.ktor.server.status)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.java.jwt)
    implementation(libs.serialization.json)
    implementation(libs.logback)
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
