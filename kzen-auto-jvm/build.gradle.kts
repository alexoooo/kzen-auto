@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
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

    ksp("tech.kzen.lib:kzen-lib-reflect-ksp:$kzenLibVersion")

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


// Icon catalogue: copy the @iconify-json/material-symbols collection JSON (downloaded by kotlinNpmInstall
// into the JS module's node_modules) into JVM resources at /icons/material-symbols.json, served on demand
// by IconCollectionHandler. Nothing imports it from Kotlin/JS, so it stays out of the esbuild bundle.
val iconCollectionDir = layout.buildDirectory.dir("generated-resources")
val copyIconCollection = tasks.register<Copy>("copyIconCollection") {
    dependsOn(rootProject.tasks.named("kotlinNpmInstall"))
    from(rootProject.layout.buildDirectory.file(
        "js/node_modules/@iconify-json/material-symbols/icons.json"))
    into(iconCollectionDir.map { it.dir("icons") })
    rename { "material-symbols.json" }
}

sourceSets.main {
    resources.srcDir(iconCollectionDir)
}


tasks.withType<ProcessResources> {
    val jsProject = project(":kzen-auto-js")

    // esbuild bundle (replaces webpack) → build/dist/js/productionExecutable/<module>.js (+ .js.map)
    val bundleTask = jsProject.tasks.named("jsEsbuildBundle")
    dependsOn(bundleTask)
    dependsOn(copyIconCollection)

    from(jsProject.layout.buildDirectory.dir("dist/js/productionExecutable")) {
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


// Forward the optional `-DjobSliceRows=<n>` to the forked test JVM, so JobExecutionTest's M3 throughput
// benchmark can be scaled for a heavier manual run (it defaults to a modest CI-friendly N otherwise).
tasks.test {
    System.getProperty("jobSliceRows")?.let {
        systemProperty("jobSliceRows", it)
    }
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.auto.server.codegen.KzenAutoJvmModule")
}


val dependenciesDir = "dependencies"
tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
        .into("${layout.buildDirectory.get().asFile}/libs/$dependenciesDir")
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