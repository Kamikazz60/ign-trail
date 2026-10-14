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

rootProject.name = "IGNTrail"

include(":app")
include(":core:model")
include(":core:geo")
include(":core:tracking")
include(":core:gpx")
include(":core:navigation")
include(":core:planning")
include(":core:stats")
include(":data:location")
include(":data:settings")
include(":data:storage")
