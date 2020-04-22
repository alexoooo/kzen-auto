plugins {
    kotlin("multiplatform") version kotlinVersion apply false
}

//buildscript {
//    repositories {
//        jcenter()
//        maven {
//            url 'https://plugins.gradle.org/m2/'
//        }
//        mavenCentral()
//    }
//
//    dependencies {
//        classpath group: 'org.jetbrains.kotlin', name: 'kotlin-gradle-plugin', version: kotlinVersion
//        classpath group: 'com.moowork.gradle', name: 'gradle-node-plugin', version: nodePluginVersion
//    }
//}


allprojects {
    group = "tech.kzen.auto"
    version = "0.13.0-SNAPSHOT"

    repositories {
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-dev") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
        jcenter()
        maven { setUrl("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
        maven { setUrl("https://dl.bintray.com/kotlin/kotlinx") }
        mavenCentral()

        maven { setUrl("https://raw.githubusercontent.com/alexoooo/kzen-repo/master/artifacts") }

        mavenLocal()
    }
}

//subprojects {
//    group = 'tech.kzen.auto'
//    version = '0.13.0-SNAPSHOT'
//
//    repositories {
//        mavenLocal()
//        jcenter()
//        maven {
//            url 'https://raw.githubusercontent.com/alexoooo/kzen-repo/master/artifacts'
//        }
//        maven {
//            url 'http://dl.bintray.com/kotlin/kotlin-js-wrappers'
//        }
//    }
//}
