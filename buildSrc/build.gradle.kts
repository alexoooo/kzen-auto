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
// that one maxes out at JVM target 25. buildSrc compiles on the Gradle daemon JVM, not the app's
// toolchain, so on a JDK-26 daemon its compileJava would target 26 while its Kotlin falls back to 25 →
// an "inconsistent JVM target" warning. This pin keeps both buildSrc compilers at 25 whatever the
// daemon runs; the app's own target is `javaVersion` in Dependencies.kt.
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "25"
    targetCompatibility = "25"
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}
