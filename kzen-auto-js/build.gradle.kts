import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn


plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
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
        jsMain.dependencies {
            implementation(project(":kzen-auto-common"))

            api("tech.kzen.lib:kzen-lib-js:$kzenLibVersion")

            implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

            implementation(kotlinWrappers.react)
            implementation(kotlinWrappers.reactDom)
            implementation(kotlinWrappers.emotion.styled)
            implementation(kotlinWrappers.mui.material)

            implementation(npm("@mui/icons-material", muiIconsVersion))
            implementation(npm("cropperjs", cropperJsVersion))
            implementation(npm("lodash", lodashVersion))
            implementation(npm("react-select", reactSelectVersion))
            implementation(npm("@iconify/react", iconifyReactVersion))
            implementation(npm("@iconify/icons-vaadin", iconifyIconsVaadinVersion))

            // NB: avoid "unmet peer dependency" warning
            implementation(npm("@babel/core", babelCoreVersion))
        }

        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}


run {}


// In watch mode the webpack output is re-emitted between Gradle's cache header read and the actual
// tarball pack, which truncates kzen-auto-js.js.map mid-store. Skip caching this task while watching.
if (devMode) {
    tasks.matching { it.name == "jsBrowserProductionWebpack" }.configureEach {
        outputs.cacheIf { false }
    }
}


dependencies {
    add("kspJs", "tech.kzen.lib:kzen-lib-reflect-ksp:$kzenLibVersion")
}


ksp {
    arg("kzen.reflect.moduleClassName", "tech.kzen.auto.client.codegen.KzenAutoJsModule")
}


publishing {
    repositories {
        mavenLocal()
    }
}


// https://youtrack.jetbrains.com/issue/KT-52578/KJS-Gradle-KotlinNpmInstallTask-gradle-task-produces-unsolvable-warning-ignored-scripts-due-to-flag.
yarn.ignoreScripts = false