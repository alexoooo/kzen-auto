plugins {
    id("org.jetbrains.kotlin.js")
    `maven-publish`
}


kotlin {
    js {
        useCommonJs()

//        produceExecutable()

        browser {
            webpackTask {
                // TODO: hot-reload breaks?
//                outputFileName = "index.js"
            }
        }
    }
}


dependencies {
    implementation(project(":kzen-auto-common"))

//    implementation("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
    api("tech.kzen.lib:kzen-lib-js:$kzenLibVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-assembly:$kotlinxHtmlVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react:$kotlinReactVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:$kotlinReactDomVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-styled:$kotlinStyledVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:$kotlinExtensionsVersion")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-css-js:$kotlinCssVersion")

    implementation(npm("react", reactVersion))
    implementation(npm("react-dom", reactVersion))
    implementation(npm("react-is", reactVersion))
    implementation(npm("inline-style-prefixer", inlineStylePrefixerVersion))
    implementation(npm("styled-components", styledComponentsVersion))
    implementation(npm("@material-ui/core", materialUiCoreVersion))
    implementation(npm("@material-ui/icons", materialUiIconsVersion))
    implementation(npm("@material-ui/lab", materialUiLabVersion))
    implementation(npm("cropperjs", cropperJsVersion))
    implementation(npm("lodash", lodashVersion))
    implementation(npm("react-select", reactSelectVersion))

    testImplementation(kotlin("test"))
}


run {}


publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("js") {
//            println("Components: " + components.asMap.keys)
            from(components["kotlin"])
        }
    }
}