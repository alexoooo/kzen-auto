plugins {
    kotlin("multiplatform") version kotlinVersion apply false
    id("com.google.devtools.ksp") version kspVersion apply false
}


allprojects {
    group = "tech.kzen.auto"
    version = "0.29.1"

    repositories {
        mavenCentral()

        maven { setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }

        maven { setUrl("https://raw.githubusercontent.com/alexoooo/kzen-repo/master/artifacts") }

        mavenLocal()
    }
}