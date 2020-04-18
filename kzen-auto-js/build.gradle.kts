plugins {
    id("org.jetbrains.kotlin.js")
}


kotlin {
    target {
        useCommonJs()

//        produceExecutable()

        browser {
            // TODO: figure out keep("tech.kzen.**") pattern
            dceTask {
                dceOptions {
                    devMode = true
                }
            }
        }
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-js")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:$coroutinesVersion")

    implementation(project(":kzen-auto-common"))

    implementation(npm("core-js", coreJsVersion))
    implementation("org.jetbrains.kotlinx:kotlinx-html-assembly:$kotlinxHtmlVersion")
    implementation("org.jetbrains:kotlin-react:$kotlinxReactVersion")
    implementation("org.jetbrains:kotlin-react-dom:$kotlinxReactDomVersion")
    implementation("org.jetbrains:kotlin-styled:$kotlinxStyledVersion")
    implementation("org.jetbrains:kotlin-extensions:$kotlinxExtensionsVersion")
    implementation("org.jetbrains:kotlin-css-js:$kotlinxCssVersion")
    implementation(npm("react", reactVersion))
    implementation(npm("react-dom", reactVersion))
    implementation(npm("react-is", reactVersion))
    implementation(npm("inline-style-prefixer", inlineStylePrefixerVersion))
    implementation(npm("styled-components", styledComponentsVersion))
    testImplementation("org.jetbrains.kotlin:kotlin-test-js")
    testImplementation(npm("enzyme", "3.9.0"))
    testImplementation(npm("enzyme-adapter-react-16", "1.12.1"))

    implementation("tech.kzen.lib:kzen-lib-common-js:$kzenLibVersion")
    implementation("tech.kzen.lib:kzen-lib-js:$kzenLibVersion")

    implementation(npm("@material-ui/core", "4.9.7"))
    implementation(npm("@material-ui/icons", "4.9.1"))
    implementation(npm("cropperjs", "1.5.6"))
    implementation(npm("lodash", "4.17.15"))
    implementation(npm("react-select", "3.0.8"))
}


run {}