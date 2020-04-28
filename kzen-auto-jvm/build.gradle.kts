import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack


plugins {
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version dependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version kotlinVersion
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
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

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
}


tasks.withType<ProcessResources> {
    val jsProject = project(":kzen-auto-js")
    val task = jsProject.tasks.getByName("browserProductionWebpack") as KotlinWebpack

    from(task.destinationDirectory!!) {
        into("public")
    }

    dependsOn(task)
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}


tasks.getByName<Jar>("jar") {
    enabled = true
}


tasks.getByName<BootJar>("bootJar") {
    archiveClassifier.set("boot")
}