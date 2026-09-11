import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "streaming-platform"

include(
    ":shared:common",
    ":shared:core",
    ":shared:network",
    ":shared:database",
    ":shared:security",
    ":shared:authentication",
    ":shared:user",
    ":shared:profile",
    ":shared:content",
    ":shared:search",
    ":shared:playback",
    ":shared:watchlist",
    ":shared:testing",
    ":backend:identity-service",
    ":backend:profile-service",
    ":backend:content-service",
    ":backend:search-service",
    ":backend:playback-service",
    ":backend:watchlist-service",
    ":backend:api-gateway",
    ":streaming:media-processing",
    ":streaming:storage",
    ":backend:media-service",
    ":backend:media-worker",
    ":backend:drm-service",
    ":apps:androidApp",
    ":shared:iosKit",
)
