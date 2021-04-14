repositories {
//    jcenter()
    mavenCentral()
    gradlePluginPortal()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    compile("com.github.maxm123:shadow:master-SNAPSHOT")
//    classpath("com.github.maxm123:shadow:master-SNAPSHOT")
}

plugins {
    `kotlin-dsl`
}


//buildscript {
//    repositories {
//        gradlePluginPortal()
//        maven { setUrl("https://jitpack.io") }
//    }
//
//}
