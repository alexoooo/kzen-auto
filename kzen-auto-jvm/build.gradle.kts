@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }
}


dependencies {
    implementation(project(":kzen-auto-common"))
    api(project(":kzen-auto-plugin"))

//    implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
    api("tech.kzen.lib:kzen-lib-jvm:$kzenLibVersion")

//    api("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

//    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
//    implementation("org.jetbrains.kotlin-wrappers:kotlin-css-jvm:$kotlinCssVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlin")
    api("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("it.unimi.dsi:fastutil-core:$fastutilVersion")
    implementation("io.lacuna:bifurcan:$bifurcanVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("com.lmax:disruptor:$disruptorVersion")
    implementation("com.sangupta:bloomfilter:$bloomFilterVersion")
    implementation("commons-io:commons-io:$commonsIoVersion")
    implementation("com.linkedin.migz:migz:$migzVersion")
    implementation("javax.annotation:javax.annotation-api:$annotationsApiVersion")

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
    val task = jsProject.tasks.getByName("browserProductionWebpack") as org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

    from(task.destinationDirectory) {
        into("static")
    }

    dependsOn(task)
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xjsr305=strict")
        jvmTarget = jvmTargetVersion
    }
}


tasks.compileJava {
    options.release.set(javaVersion)
}


val dependenciesDir = "dependencies"
task("copyDependencies", Copy::class) {
    from(configurations.default).into("$buildDir/libs/$dependenciesDir")
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