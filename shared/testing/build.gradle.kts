plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:common"))
            api(project(":shared:core"))
            implementation(libs.kotlin.test)
        }
    }
}
