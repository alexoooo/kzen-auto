rootProject.name = "kzen-auto"

include("kzen-auto-common", "kzen-auto-js", "kzen-auto-jvm")
include("kzen-auto-plugin")
include("kzen-auto-test")


dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("kotlinWrappers") {
            val wrappersVersion = "2026.5.3"
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
        }
    }
}