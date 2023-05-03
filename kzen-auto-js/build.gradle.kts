import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode


plugins {
    id("org.jetbrains.kotlin.js")
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
}


dependencies {
    implementation(project(":kzen-auto-common"))

//    implementation("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
    api("tech.kzen.lib:kzen-lib-js:$kzenLibVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-html-assembly:$kotlinxHtmlVersion")

    implementation("org.jetbrains.kotlin-wrappers:kotlin-react:$kotlinReactVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:$kotlinReactDomVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion:$kotlinEmotionVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-mui:$kotlinMuiVersion")

////    implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:$kotlinStyledVersion")
//    implementation("org.jetbrains.kotlin-wrappers:kotlin-styled-next:$kotlinStyledVersion")
//    implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:$kotlinExtensionsVersion")
//    implementation("org.jetbrains.kotlin-wrappers:kotlin-css-js:$kotlinCssVersion")
//
////    implementation(npm("@material-ui/lab", materialUiLabVersion))
    implementation(npm("@mui/icons-material", muiIconsVersion))

    implementation(npm("cropperjs", cropperJsVersion))
    implementation(npm("lodash", lodashVersion))
    implementation(npm("react-select", reactSelectVersion))
    implementation(npm("@iconify/react", iconifyReactVersion))
    implementation(npm("@iconify/icons-vaadin", iconifyIconsVaadinVersion))
//    implementation(npm("@iconify/icons-vaadin/area-select", iconifyIconsVaadinVersion))

    testImplementation(kotlin("test"))
}


//configurations.implementation {
//    exclude(group = "org.jetbrains.kotlin-wrappers", module = "kotlin-react-legacy")
//    exclude(group = "org.jetbrains.kotlin-wrappers", module = "kotlin-react-dom-legacy")
//    exclude(group = "org.jetbrains.kotlin-wrappers", module = "kotlin-react-dom")
//}


run {}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("js") {
            from(components["kotlin"])
        }
    }
}


// https://youtrack.jetbrains.com/issue/KT-49124
//rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
//    versions.webpackCli.version = "4.9.0"
//}