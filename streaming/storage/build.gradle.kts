plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.minio)
    implementation(libs.aws.s3)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
}

tasks.test { useJUnitPlatform() }
