plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}

// TODO: getting the following warnings:
//  - 'compileJava' task (current target is 18) and 'compileKotlin' task (current target is 1.8)
//      jvm target compatibility should be set to the same Java version.
//  - TestReport.destinationDir property has been deprecated
allprojects {
    group = "tech.kzen.auto"
    version = "0.25.0-SNAPSHOT"

    repositories {
        mavenCentral()

        maven { setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
        maven { setUrl("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }

        maven { setUrl("https://raw.githubusercontent.com/alexoooo/kzen-repo/master/artifacts") }

        mavenLocal()
    }
}