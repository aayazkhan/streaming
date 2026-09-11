plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:common"))
            api(project(":shared:core"))
            implementation(libs.sqldelight.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.jdbc)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

sqldelight {
    databases {
        create("StreamingDatabase") {
            packageName.set("com.streaming.platform.database.generated")
        }
    }
}
