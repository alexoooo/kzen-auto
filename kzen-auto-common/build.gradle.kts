plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {}

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
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains:kotlin-css:$kotlinxCssVersion")
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:$coroutinesVersion")
//                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")

                implementation("tech.kzen.lib:kzen-lib-common-metadata:$kzenLibVersion")
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
                implementation(kotlin("stdlib-jdk8"))
                implementation("ch.qos.logback:logback-classic:$logbackVersion")
                implementation("org.jetbrains:kotlin-css-jvm:$kotlinxCssVersion")
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
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
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serializationVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")
                implementation("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
                implementation(npm("immutable", immutaleJsVersion))
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

//dependencies {
//    compile group: 'org.jetbrains.kotlin', name: 'kotlin-stdlib-common', version: kotlinVersion
//    compile "org.jetbrains.kotlinx:kotlinx-coroutines-core-common:${kotlinxCoroutinesCoreVersion}"
//
//    compile group: 'tech.kzen.lib', name: 'kzen-lib-common', version: kzenLibVersion
//
//    testCompile group: 'org.jetbrains.kotlin', name: 'kotlin-test-common', version: kotlinVersion
//    testCompile group: 'org.jetbrains.kotlin', name: 'kotlin-test-annotations-common', version: kotlinVersion
//}
//
//
//kotlin {
//    jvm()
//    js {
//        browser {
//            testTask {
//                testLogging {
//                    showExceptions = true
//                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
//                    showCauses = true
//                    showStackTraces = true
//                }
//            }
//        }
////        nodejs {
////            testTask {
////                testLogging {
////                    showExceptions = true
////                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
////                    showCauses = true
////                    showStackTraces = true
////                }
////            }
////        }
//    }
//
//    sourceSets {
//        commonMain {
//            dependencies {
//                implementation kotlin('stdlib-common')
////                implementation "org.jetbrains:kotlin-css:1.0.0-$kotlin_version"
////                implementation "org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinx_serialization_version"
//            }
//        }
//        commonTest {
//            dependencies {
//                implementation kotlin('test-common')
//                implementation kotlin('test-annotations-common')
//            }
//        }
//        jvmMain {
//            dependencies {
//                implementation kotlin('stdlib-jdk8')
////                implementation "io.ktor:ktor-server-netty:$ktor_version"
////                implementation "io.ktor:ktor-client-apache:$ktor_version"
////                implementation "io.ktor:ktor-jackson:$ktor_version"
////                implementation "io.ktor:ktor-html-builder:$ktor_version"
////                implementation "ch.qos.logback:logback-classic:$logback_version"
////                implementation "org.jetbrains:kotlin-css-jvm:1.0.0-$kotlin_version"
////                implementation "org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinx_serialization_version"
////                implementation "org.jetbrains.exposed:exposed-core:0.22.1"
////                implementation "org.jetbrains.exposed:exposed-dao:0.22.1"
////                implementation "org.jetbrains.exposed:exposed-jdbc:0.22.1"
////                implementation 'com.h2database:h2:1.4.200'
//            }
//        }
//        jvmTest {
//            dependencies {
//                implementation kotlin('test')
//                implementation kotlin('test-junit')
//            }
//        }
//
//        jsMain {
//            dependencies {
//                implementation kotlin('stdlib-js')
////                implementation "org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$kotlinx_serialization_version"
//                implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.3.3"
//            }
//        }
//        jsTest {
//            dependencies {
//                implementation kotlin('test-js')
//            }
//        }
//    }
//}