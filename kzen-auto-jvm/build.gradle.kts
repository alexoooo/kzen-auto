@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


plugins {
    kotlin("jvm")
    // SER3: resolves the @Serializable common DTOs' generated serializers at compile time for
    // serverJson.encodeToString(dto) (see KzenAutoMain.respondJson). Without it the call falls back to
    // runtime lookup and fails at runtime rather than compile time. The kotlinx-serialization-json runtime
    // arrives transitively via kzen-auto-common's api(...) — no dependency line needed here.
    kotlin("plugin.serialization")
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

    // kotlin.reflect.full.* (allSupertypes) — IterableElementTypeReflect recovers an Iterable's element
    // type from the class hierarchy. Already on the classpath transitively via the scripting deps below;
    // declared explicitly because it's used directly.
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")

    // Kotlin PSI parser (KotlinSyntaxValidator parses expressions whose scope isn't statically known).
    // Arrives at runtime scope only via kotlin-scripting-jvm-host, so it needs declaring to be compiled
    // against; adds no artifact. NB: this jar shades IntelliJ into org.jetbrains.kotlin.com.intellij.*.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

    api("ch.qos.logback:logback-classic:$logbackVersion")
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

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")
    implementation("jakarta.annotation:jakarta.annotation-api:$annotationsApiVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-server-compression:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
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


// Build stamp: version + build timestamp written into the generated-resources dir (already a resource
// srcDir above), so it lands in the jar at /kzen-auto-build.properties. Read at startup by BuildInfo
// and surfaced as logo hover text (see indexPage / HeaderController). Deliberately never up-to-date so
// every build re-stamps the moment of build — only resource processing + the thin jar re-run, not
// Kotlin compilation. The resource name is module-specific: kzen-project-jvm carries kzen-auto-jvm.jar
// on its classpath too, so a shared name would collide.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val buildInfoFile = iconCollectionDir.map { it.file("kzen-auto-build.properties") }
    val buildVersion = version.toString()
    outputs.file(buildInfoFile)
    outputs.upToDateWhen { false }
    doLast {
        val timestamp = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        buildInfoFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("version=$buildVersion\ntimestamp=$timestamp\n")
        }
    }
}


tasks.withType<ProcessResources> {
    val jsProject = project(":kzen-auto-js")

    // esbuild bundle (replaces webpack) → build/dist/js/productionExecutable/<module>.js (+ .js.map)
    val bundleTask = jsProject.tasks.named("jsEsbuildBundle")
    dependsOn(bundleTask)
    dependsOn(copyIconCollection)
    dependsOn(generateBuildInfo)

    from(jsProject.layout.buildDirectory.dir("dist/js/productionExecutable")) {
        into("static")
    }
}


// Deterministic bundle refresh for the frontend dev launch. FrontendDevelopment.kt serves the esbuild bundle
// straight from kzen-auto-js/build/dist/js/productionExecutable/, and that bundle can silently go stale: the
// Kotlin/JS DEVELOPMENT and PRODUCTION executables' compileSync tasks write the SAME per-module dir
// (build/js/packages/<pkg>/kotlin — esbuild's input; see the same ambiguity flagged in kzen-auto-js's
// build.gradle.kts), so after a production build (jar / build / publishToMavenLocal / an umbrella build) an
// up-to-date `-PjsWatch` dev sync can leave the prod bundle in place and esbuild re-bundles stale content —
// the "I must run `clean` first" symptom. Wiping ONLY those two artifacts (the shared compileSync destination
// + the final bundle) before the JS pipeline runs makes a single `frontendDevelopment` deterministically
// rebundle the current sources in the current mode. Kotlin compile outputs / incremental caches are left
// intact, so recompilation stays incremental; the standalone `-t :kzen-auto-js:jsEsbuildBundle` watch loop is
// untouched (this clean only enters the graph via frontendDevelopment).
val jsBundleProject = project(":kzen-auto-js")
val cleanFrontendBundle = tasks.register<Delete>("cleanFrontendBundle") {
    description = "Wipe the stale-prone JS bundle artifacts so frontendDevelopment serves the latest UI"
    delete(
        rootProject.layout.buildDirectory.dir("js/packages/${rootProject.name}-${jsBundleProject.name}/kotlin"),
        jsBundleProject.layout.buildDirectory.dir("dist/js/productionExecutable"))
}

// The JS bundle producers must run AFTER the wipe (else compileSync/esbuild could run first and be deleted,
// or esbuild could bundle an emptied input dir). mustRunAfter is a no-op when cleanFrontendBundle isn't in
// the graph, so direct `:kzen-auto-js:jsEsbuildBundle` invocations (the watch loop) are unaffected.
jsBundleProject.tasks.matching {
    it.name == "jsDevelopmentExecutableCompileSync" ||
        it.name == "jsProductionExecutableCompileSync" ||
        it.name == "jsEsbuildBundle"
}.configureEach {
    mustRunAfter(cleanFrontendBundle)
}


// Single-command frontend dev launch (alternative to an IDE run config — see FrontendDevelopment.kt):
//   ./gradlew :kzen-auto-jvm:frontendDevelopment -PjsWatch
// `cleanFrontendBundle` wipes the stale-prone bundle artifacts and `classes` pulls in processResources ->
// jsEsbuildBundle, so the served bundle is deterministically rebuilt from the latest sources before the server
// binds. With -PjsWatch that's the unminified DEVELOPMENT executable (symbols preserved, faster build); without
// it, the minified production bundle. workingDir is the kzen-auto root because FrontendDevelopment resolves
// kzen-auto-js/build/dist/... relative to the process cwd (an IDE launch runs with cwd = project root).
// Debuggable from IntelliJ's Gradle tool window (right-click → Debug).
tasks.register<JavaExec>("frontendDevelopment") {
    group = "application"
    description = "Run FrontendDevelopment, deterministically rebuilding the JS bundle first (-PjsWatch for the dev/unminified bundle)"
    dependsOn(cleanFrontendBundle)
    dependsOn("classes")
    mainClass.set("tech.kzen.auto.server.dev.FrontendDevelopmentKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir
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


// The module class name above is module-global, so a test-source pass emits a SECOND object under that same
// FQN — and test output precedes the main classes on the test runtime classpath, so it shadows the real one
// and silently drops every production registration (the graph then falls through to the JVM reflective
// mirror). Test fixtures are `@Reflect`-annotated and served by that mirror instead.
tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    enabled = false
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
    // allSource includes the generated-resources srcDir (copyIconCollection's + generateBuildInfo's
    // output, registered above), so sourcesJar consumes those tasks' output — declare the dependencies
    // (Gradle task-validation would otherwise fail the publish, which is the only path that builds
    // sourcesJar).
    dependsOn(copyIconCollection)
    dependsOn(generateBuildInfo)
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