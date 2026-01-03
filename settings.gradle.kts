rootProject.name = "kzen-auto"

include("kzen-auto-common", "kzen-auto-js", "kzen-auto-jvm")
include("kzen-auto-plugin")


dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("kotlinWrappers") {
            val wrappersVersion = "2025.12.11"
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
        }
    }
}