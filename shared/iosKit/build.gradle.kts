import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    applyDefaultHierarchyTemplate()

    // A single-arch .framework only embeds into Xcode for the exact slice it was built for — the
    // XCFramework wrapper bundles the device (iosArm64) and Apple Silicon simulator
    // (iosSimulatorArm64) slices together so Xcode picks the right one automatically for whichever
    // destination is building, the same as any other third-party SDK distributed as an XCFramework.
    val xcf = XCFramework("SharedKit")

    val frameworkTargets = listOf(iosArm64(), iosSimulatorArm64())

    frameworkTargets.forEach { target ->
        target.binaries.framework {
            baseName = "SharedKit"
            isStatic = true
            export(project(":shared:common"))
            export(project(":shared:core"))
            export(project(":shared:security"))
            export(project(":shared:authentication"))
            export(project(":shared:profile"))
            export(project(":shared:content"))
            export(project(":shared:search"))
            export(project(":shared:playback"))
            export(project(":shared:watchlist"))
            export(project(":shared:network"))
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:common"))
            api(project(":shared:core"))
            api(project(":shared:security"))
            api(project(":shared:authentication"))
            api(project(":shared:profile"))
            api(project(":shared:content"))
            api(project(":shared:search"))
            api(project(":shared:playback"))
            api(project(":shared:watchlist"))
            api(project(":shared:network"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.ktor.serialization.json.core)
            }
        }
    }
}
