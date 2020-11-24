
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar


plugins {
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version dependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version kotlinVersion
    `maven-publish`
}


dependencies {
//    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains:kotlin-css-jvm:1.0.0-$wrapperKotlinVersion")

    implementation(project(":kzen-auto-common"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
    implementation("tech.kzen.lib:kzen-lib-jvm:$kzenLibVersion")

    implementation("com.github.andrewoma.dexx:collection:$dexxVersion")

    implementation(group = "com.google.guava", name = "guava", version = guavaVersion)
    implementation(group = "org.seleniumhq.selenium", name = "selenium-java", version = seleniumVersion)
    implementation(group = "org.apache.commons", name = "commons-compress", version = commonsCompressVersion)
    implementation(group = "it.unimi.dsi", name = "fastutil", version = fastutilVersion)
//    implementation(group = "net.openhft", name = "chronicle-map", version = chronicleMapVersion)
//    implementation(group = "org.mapdb", name = "mapdb", version = mapDbVersion)
    implementation(group = "io.lacuna", name = "bifurcan", version = bifurcanVersion)
    implementation(group = "com.h2database", name = "h2", version = h2Version)
    implementation(group = "net.openhft", name = "zero-allocation-hashing", version = zeroAllocationHashingVersion)

//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")

    implementation("org.apache.commons:commons-csv:$commonsCsvVersion")
    implementation("commons-io:commons-io:$commonsIoVersion")
}


tasks.withType<ProcessResources> {
    val jsProject = project(":kzen-auto-js")
    val task = jsProject.tasks.getByName("browserProductionWebpack") as KotlinWebpack

    from(task.destinationDirectory) {
        into("public")
    }

    dependsOn(task)
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
//        jvmTarget = "13"
        jvmTarget = "15"
    }
}


tasks.getByName<Jar>("jar") {
    enabled = true
}


tasks.getByName<BootJar>("bootJar") {
    archiveClassifier.set("boot")
}


val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}
//tasks {
//    val sourcesJar by creating(Jar::class) {
//        archiveClassifier.set("sources")
//        from(sourceSets.getByName("main").allSource)
//    }
//
//    artifacts {
//        archives(sourcesJar)
//        archives(jar)
//    }
//}

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