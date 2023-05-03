plugins {
    kotlin("multiplatform")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }

    jvm {
        @Suppress("UNUSED_VARIABLE")
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = jvmTargetVersion
            }
        }
    }

    js {
        browser {
            testTask {
                testLogging {
                    showExceptions = true
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showStackTraces = true
                }
            }
        }
    }

    sourceSets {
        @Suppress("UNUSED_VARIABLE")
        val commonMain by getting {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common:$kzenLibVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
//                implementation("org.jetbrains.kotlin-wrappers:kotlin-css:$kotlinCssVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }


        @Suppress("UNUSED_VARIABLE")
        val jvmMain by getting {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")

                implementation("ch.qos.logback:logback-classic:$logbackVersion")
//                implementation("org.jetbrains.kotlin-wrappers:kotlin-css-jvm:$kotlinCssVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jvmTest by getting {
            dependencies {}
        }


        @Suppress("UNUSED_VARIABLE")
        val jsMain by getting {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jsTest by getting {
            dependencies {}
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("common") {
//            println("Components: " + components.asMap.keys)
            from(components["kotlin"])
//            artifact(sourcesJar.get())
        }
    }
}