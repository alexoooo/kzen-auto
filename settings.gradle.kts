//include 'kzen-auto-common', 'kzen-auto-js', 'kzen-auto-jvm'

pluginManagement {
//    resolutionStrategy {
//        eachPlugin {
//            if (requested.id.id == "kotlinx-serialization") {
//                useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
//            }
//        }
//    }

    repositories {
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-dev") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
        mavenCentral()
        maven { setUrl("https://plugins.gradle.org/m2/") }
    }
}

include("kzen-auto-common", "kzen-auto-js", "kzen-auto-jvm")