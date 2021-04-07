plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}


allprojects {
    group = "tech.kzen.auto"
    version = "0.22.0-SNAPSHOT"

    repositories {
        mavenCentral()
        jcenter()

        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-dev") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlinx") }

        maven { setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }

        maven { setUrl("https://raw.githubusercontent.com/alexoooo/kzen-repo/master/artifacts") }

        mavenLocal()
    }
}