import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn


plugins {
    kotlin("multiplatform")
    `maven-publish`
}


val devMode = properties.containsKey("jsWatch")


kotlin {
    js {
        useCommonJs()
        binaries.executable()

        browser {
            val webpackMode =
                if (devMode) {
                    Mode.DEVELOPMENT
                }
                else {
                    Mode.PRODUCTION
                }

            commonWebpackConfig {
                mode = webpackMode
            }
        }

        // TODO: remove once browserDevelopmentWebpack works in continuous mode
        if (devMode) {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions.freeCompilerArgs.add("-Xir-minimized-member-names=false")
                }
            }
        }

        // TODO: how to pass NODE_OPTIONS to nodejs to avoid "Allocation failed"?
//        nodejs {
//            nodeOptions = ["--max-old-space-size=4096"]
//        }
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":kzen-auto-common"))

                api("tech.kzen.lib:kzen-lib-js:$kzenLibVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:$kotlinReactVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:$kotlinReactDomVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:$kotlinEmotionVersion")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-mui:$kotlinMuiVersion")

                implementation(npm("@mui/icons-material", muiIconsVersion))
                implementation(npm("cropperjs", cropperJsVersion))
                implementation(npm("lodash", lodashVersion))
                implementation(npm("react-select", reactSelectVersion))
                implementation(npm("@iconify/react", iconifyReactVersion))
                implementation(npm("@iconify/icons-vaadin", iconifyIconsVaadinVersion))

                // NB: avoid "unmet peer dependency" warning
                implementation(npm("@babel/core", babelCoreVersion))
            }
        }

        jsTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}


run {}


publishing {
    repositories {
        mavenLocal()
    }
}


// https://youtrack.jetbrains.com/issue/KT-52578/KJS-Gradle-KotlinNpmInstallTask-gradle-task-produces-unsolvable-warning-ignored-scripts-due-to-flag.
yarn.ignoreScripts = false