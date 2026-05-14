@file:Suppress("UnstableApiUsage")

import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack


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
    implementation(project(":kzen-auto-common"))
    api(project(":kzen-auto-plugin"))

//    implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
    api("tech.kzen.lib:kzen-lib-jvm:$kzenLibVersion")

    api("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")

    api("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("tools.jackson.module:jackson-module-kotlin:$jacksonModuleKotlin")
    api("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    implementation("io.github.bonigarcia:webdrivermanager:$webdrivermanagerVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("it.unimi.dsi:fastutil-core:$fastutilVersion")
    implementation("io.lacuna:bifurcan:$bifurcanVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.lmax:disruptor:$disruptorVersion")
    implementation("com.sangupta:bloomfilter:$bloomFilterVersion")
    implementation("commons-io:commons-io:$commonsIoVersion")
    implementation("com.linkedin.migz:migz:$migzVersion")
//    implementation("javax.annotation:javax.annotation-api:$annotationsApiVersion")
    implementation("jakarta.annotation:jakarta.annotation-api:$annotationsApiVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
//    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinxHtmlVersion")

    testImplementation(kotlin("test"))
}


tasks.withType<ProcessResources> {
    val jsProject = project(":kzen-auto-js")

    val browserDistributionTask = jsProject.tasks.getByName("jsBrowserDistribution")
    dependsOn(browserDistributionTask)

    val task = jsProject.tasks.getByName("jsBrowserProductionWebpack") as KotlinWebpack
    dependsOn(task)

    from(task.outputDirectory) {
        into("static")
    }
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


val dependenciesDir = "dependencies"
tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
        .into("${layout.buildDirectory.get().asFile}/libs/$dependenciesDir")
}


// Codegen — rewrites the three KzenAuto*Module.kt files by reflection over the source tree.
// The generator mains live in src/test/kotlin and resolve their output paths relative to the
// kzen-auto root, so workingDir is pinned to rootProject.rootDir.
val codegenWorkingDir = rootProject.rootDir
val codegenClasspath = sourceSets["test"].runtimeClasspath

tasks.register<JavaExec>("runCommonCodegen") {
    group = "codegen"
    description = "Regenerate kzen-auto-common/.../codegen/KzenAutoCommonModule.kt"
    mainClass.set("tech.kzen.auto.server.codegen.KzenAutoCommonCodegen")
    classpath = codegenClasspath
    workingDir = codegenWorkingDir
}

tasks.register<JavaExec>("runJsCodegen") {
    group = "codegen"
    description = "Regenerate kzen-auto-js/.../codegen/KzenAutoJsModule.kt"
    mainClass.set("tech.kzen.auto.server.codegen.KzenAutoJsCodegen")
    classpath = codegenClasspath
    workingDir = codegenWorkingDir
}

tasks.register<JavaExec>("runJvmCodegen") {
    group = "codegen"
    description = "Regenerate kzen-auto-jvm/.../codegen/KzenAutoJvmModule.kt"
    mainClass.set("tech.kzen.auto.server.codegen.KzenAutoJvmCodegen")
    classpath = codegenClasspath
    workingDir = codegenWorkingDir
}

tasks.register<JavaExec>("runAllCodegen") {
    group = "codegen"
    description = "Regenerate all three KzenAuto*Module.kt codegen files"
    mainClass.set("tech.kzen.auto.server.codegen.KzenAutoAllCodegen")
    classpath = codegenClasspath
    workingDir = codegenWorkingDir
}


tasks.getByName<Jar>("jar") {
    val jvmProject = project(":kzen-auto-jvm")
    val copyDependenciesTask = jvmProject.tasks.getByName("copyDependencies") as Copy
    dependsOn(copyDependenciesTask)

    manifest {
        attributes["Main-Class"] = "tech.kzen.auto.server.KzenAutoMainKt"
        attributes["Class-Path"] = configurations
            .runtimeClasspath
            .get()
            .joinToString(separator = " ") { file ->
                "$dependenciesDir/${file.name}"
            }
    }
}


val sourcesJar by tasks.registering(Jar::class) {
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