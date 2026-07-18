import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
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
    implementation(kotlin("reflect"))
    implementation(project(":kzen-auto-jvm"))
    // Harness reads back logic-trace values over REST (TesterClient), so it needs the shared
    // wire/convention types (CommonRestApi, LogicConventions, StepTrace). kzen-auto-jvm depends on
    // kzen-auto-common via `implementation`, so it isn't exposed transitively — declare it directly.
    implementation(project(":kzen-auto-common"))
    implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
    implementation("tech.kzen.lib:kzen-lib-jvm:$kzenLibVersion")

    ksp("tech.kzen.lib:kzen-lib-reflect-ksp:$kzenLibVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // TesterClient decodes REST bodies with a hand-built kotlinx Json (no Ktor ContentNegotiation), so it
    // needs only the raw HTTP client. kotlinx-serialization-json arrives transitively via kzen-auto-common's
    // api(...) export. (SER5 removed jackson-module-kotlin, ktor-client-content-negotiation, and
    // ktor-serialization-jackson — kzen-auto-test is Jackson-free.)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.compileJava {
    options.release.set(javaVersion)
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.auto.test.codegen.KzenAutoTestModule")
}


tasks.named<Test>("test") {
    useJUnitPlatform()
    // Default `test` is reserved for any future fast harness unit tests; the
    // expensive end-to-end suite runs from the `selfTest` task instead, so
    // umbrella `./gradlew build` does not spawn Chrome on every aggregate build.
    exclude("**/*SelfTest.class")
}


tasks.register<JavaExec>("runTester") {
    description = "Launch the tester kzen-auto with kzen-auto-test on classpath (CLI equivalent of the IDE Tester run config)."
    group = "application"
    val jvmJar = project(":kzen-auto-jvm").tasks.named<Jar>("jar")
    dependsOn(jvmJar)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("tech.kzen.auto.test.TesterMain")
    workingDir = projectDir
    // No port arg: TesterMain defaults to TesterMain.TESTER_PORT
    systemProperty(
        "kzenAutoJar",
        project.findProperty("kzenAutoJar")?.toString()
            ?: jvmJar.flatMap { it.archiveFile }.get().asFile.absolutePath)
}


val selfTest = tasks.register<Test>("selfTest") {
    description = "Blackbox end-to-end self-tests that spawn a tester kzen-auto JVM; the tester's Scripts spawn the SUT."
    group = "verification"
    useJUnitPlatform()
    include("**/*SelfTest.class")
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    // Each test class spawns the tester JVM (plus, via the script, a SUT JVM and Chrome); run serially.
    maxParallelForks = 1

    val jvmJar = project(":kzen-auto-jvm").tasks.named<Jar>("jar")
    val testJar = tasks.named<Jar>("jar")
    dependsOn(jvmJar, testJar)

    val resolvedKzenAutoJar = project.findProperty("kzenAutoJar")?.toString()
        ?: jvmJar.flatMap { it.archiveFile }.get().asFile.absolutePath
    systemProperty("kzenAutoJar", resolvedKzenAutoJar)
    systemProperty(
        "testerClasspath",
        listOf(
            testJar.flatMap { it.archiveFile }.get().asFile.absolutePath,
            resolvedKzenAutoJar
        ).joinToString(File.pathSeparator))
    systemProperty("testerMainClass", "tech.kzen.auto.test.TesterMain")

    // Optional pin. Absent (the norm), SelfTestBase takes a free port, so a self-test never contends
    // with an interactive tester on TesterMain.TESTER_PORT. Pass -PtesterPort=<n> when you want to open
    // the self-test's own tester in a browser mid-run.
    project.findProperty("testerPort")?.toString()?.let {
        systemProperty("testerPort", it)
    }
}
