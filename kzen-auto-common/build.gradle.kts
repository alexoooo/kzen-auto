import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    `maven-publish`
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmToolchainVersion))
    }

    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
        }
//        val main by compilations.getting {
//            kotlinOptions {
//                jvmTarget = jvmTargetVersion
//            }
//        }
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
        commonMain.dependencies {
            api("tech.kzen.lib:kzen-lib-common:$kzenLibVersion")
            api("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }


        jvmMain.dependencies {
            api("tech.kzen.lib:kzen-lib-common-jvm:$kzenLibVersion")
            api("ch.qos.logback:logback-classic:$logbackVersion")
        }

        jvmTest.dependencies {}

        jsMain.dependencies {
            api("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
        }

        jsTest.dependencies {}
    }
}


dependencies {
    add("kspCommonMainMetadata", "tech.kzen.lib:kzen-lib-reflect-ksp:$kzenLibVersion")
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.auto.common.codegen.KzenAutoCommonModule")
}


// KSP commonMain output isn't picked up by per-target compile tasks automatically — same wiring as
// kzen-lib-common.
kotlin.sourceSets.commonMain.configure {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
tasks.matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }
    .configureEach { dependsOn("kspCommonMainKotlinMetadata") }


publishing {
    repositories {
        mavenLocal()
    }
}