plugins {
    kotlin("multiplatform")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }

    jvm {
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = jvmTargetVersion
            }
        }
    }

    js {
        browser {
            testTask(Action {
                testLogging {
                    showExceptions = true
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showStackTraces = true
                }
            })
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common:$kzenLibVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
//                implementation("org.jetbrains.kotlin-wrappers:kotlin-css:$kotlinCssVersion")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }


        val jvmMain by getting {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")

                implementation("ch.qos.logback:logback-classic:$logbackVersion")
//                implementation("org.jetbrains.kotlin-wrappers:kotlin-css-jvm:$kotlinCssVersion")
            }
        }

        val jvmTest by getting {
            dependencies {}
        }


        val jsMain by getting {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
            }
        }

        val jsTest by getting {
            dependencies {}
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }

//    publications {
//        create<MavenPublication>("common") {
////            println("Components: " + components.asMap.keys)
//            from(components["kotlin"])
////            artifact(sourcesJar.get())
//        }
//    }
}