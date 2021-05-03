plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
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
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
                implementation("org.jetbrains:kotlin-css:$kotlinCssVersion")
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

                implementation("tech.kzen.lib:kzen-lib-common:$kzenLibVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }


        @Suppress("UNUSED_VARIABLE")
        val jvmMain by getting {
            dependencies {
//                implementation(kotlin("stdlib-jdk8"))
                implementation("ch.qos.logback:logback-classic:$logbackVersion")
                implementation("org.jetbrains:kotlin-css-jvm:$kotlinCssVersion")
                implementation("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }


        @Suppress("UNUSED_VARIABLE")
        val jsMain by getting {
            dependencies {
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serializationVersion")
                implementation("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
//                implementation(npm("immutable", immutaleJsVersion))
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
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