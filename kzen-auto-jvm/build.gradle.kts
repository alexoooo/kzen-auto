
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version dependencyManagementVersion

    id("com.github.johnrengelman.shadow") version shadowVersion
//    id("com.github.johnrengelman.shadow")

    kotlin("plugin.spring") version kotlinVersion
    `maven-publish`
}


dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains:kotlin-css-jvm:1.0.0-$wrapperKotlinVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlin")

    implementation(project(":kzen-auto-common"))
    implementation(project(":kzen-auto-plugin"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
    implementation("tech.kzen.lib:kzen-lib-jvm:$kzenLibVersion")

    implementation(group = "org.seleniumhq.selenium", name = "selenium-java", version = seleniumVersion)
    implementation(group = "org.apache.commons", name = "commons-compress", version = commonsCompressVersion)
    implementation(group = "it.unimi.dsi", name = "fastutil-core", version = fastutilVersion)
    implementation(group = "io.lacuna", name = "bifurcan", version = bifurcanVersion)
    implementation(group = "com.h2database", name = "h2", version = h2Version)
    implementation(group = "net.openhft", name = "zero-allocation-hashing", version = zeroAllocationHashingVersion)
    implementation(group = "com.lmax", name = "disruptor", version = disruptorVersion)
    implementation(group = "com.sangupta", name = "bloomfilter", version = bloomFilterVersion)

    implementation("commons-io:commons-io:$commonsIoVersion")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
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
        jvmTarget = jvmTargetVersion
    }
}


tasks.getByName<Jar>("jar") {
    enabled = true
}


// https://discuss.kotlinlang.org/t/kotlin-compiler-embeddable-exception-on-kotlin-script-evaluation/6547/7
// https://shareablecode.com/snippets/example-build-gradle-kt-to-build-a-shadow-jar-for-java-and-kotlin-application-ko-TYWV-i5yf
tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("kzen-auto")
    isZip64 = true
    mergeServiceFiles()
    manifest {
        // For: KzenAutoMain.kt
        attributes(mapOf("Main-Class" to "tech.kzen.auto.server.KzenAutoMainKt"))
    }
}

//tasks.getByName<BootJar>("bootJar") {
//    archiveClassifier.set("boot")
//
//    // https://discuss.kotlinlang.org/t/kotlin-compiler-embeddable-exception-on-kotlin-script-evaluation/6547/6
//    // https://github.com/sdeleuze/kotlin-script-templating
////    requiresUnpack("**/kotlin-compiler-*.jar")
//    requiresUnpack("**/*.jar")
//}


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