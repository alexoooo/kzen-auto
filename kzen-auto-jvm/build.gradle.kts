
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version dependencyManagementVersion

    id("com.github.johnrengelman.shadow") version shadowVersion

    kotlin("plugin.spring") version kotlinVersion
    `maven-publish`
}


dependencies {
    implementation(project(":kzen-auto-common"))
    implementation(project(":kzen-auto-plugin"))

//    implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
    api("tech.kzen.lib:kzen-lib-jvm:$kzenLibVersion")

//    implementation("org.springframework.boot:spring-boot-starter-webflux")
    api("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains:kotlin-css-jvm:1.0.0-$wrapperKotlinVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonModuleKotlin")
    implementation("org.seleniumhq.selenium:selenium-java:$seleniumVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("it.unimi.dsi:fastutil-core:$fastutilVersion")
    implementation("io.lacuna:bifurcan:$bifurcanVersion")
    implementation("com.h2database:h2:$h2Version")
    implementation("net.openhft:zero-allocation-hashing:$zeroAllocationHashingVersion")
    implementation("com.lmax:disruptor:$disruptorVersion")
    implementation("com.sangupta:bloomfilter:$bloomFilterVersion")
    implementation("commons-io:commons-io:$commonsIoVersion")

    testImplementation(kotlin("test"))
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