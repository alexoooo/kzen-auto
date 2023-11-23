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
        commonMain  {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common:$kzenLibVersion")
                api("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }


        jvmMain {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
                api("ch.qos.logback:logback-classic:$logbackVersion")
            }
        }

        jvmTest {
            dependencies {}
        }


        jsMain {
            dependencies {
                api("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
            }
        }

        jsTest {
            dependencies {}
        }
    }
}


publishing {
    repositories {
        mavenLocal()
    }
}