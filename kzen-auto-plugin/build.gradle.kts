import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
    }
}


dependencies {
    api("net.openhft:zero-allocation-hashing:$zeroAllocationHashingVersion")
}


//tasks.withType<KotlinCompile> {
//    kotlinOptions {
//        freeCompilerArgs += listOf("-Xjsr305=strict")
//        jvmTarget = jvmTargetVersion
//    }
//}


tasks.compileJava {
    options.release.set(javaVersion)
}


val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("jvm") {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}