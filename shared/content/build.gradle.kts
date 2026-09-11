plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:common"))
            api(project(":shared:core"))
            api(project(":shared:network"))
            implementation(libs.datetime)
            implementation(libs.serialization.json)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
