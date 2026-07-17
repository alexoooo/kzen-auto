import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()
    gradlePluginPortal()
}

plugins {
    `kotlin-dsl`
}


// buildSrc is compiled by Gradle's *embedded* Kotlin (2.3.21 in Gradle 9.6.1), NOT the project's
// Kotlin 2.4.0 — a `kotlin-dsl` module must use the compiler bundled in the Gradle distribution, and
// that one maxes out at JVM target 25. With the JDK-26 toolchain the app targets, buildSrc's compileJava
// would inherit 26 while its Kotlin falls back to 25 → an "inconsistent JVM target" warning. Pinning
// ONLY buildSrc down to 25 keeps the app on 26 and silences the warning. Remove once Gradle ships an
// embedded Kotlin that supports JVM target 26.
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}
