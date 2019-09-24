package tech.kzen.auto.server.service.webdriver.model


enum class BrowserLauncher(
        val binaryFilenames: List<String>,
        val driverSystemProperty: String
) {
//    InternetExplorer(
//            listOf(
//                    "IEDriverServer.exe"
//            ),
//            "webdriver.ie.driver"
//    ),

    GoogleChrome(
            listOf(
                    "chromedriver.exe",
                    "chromedriver"
            ),
            "webdriver.chrome.driver"
    ),

//    PhantomJs(
//            listOf(
//                    "phantomjs.exe",
//                    "phantomjs"
//            ),
//            "phantomjs.binary.path"
//    ),

//    OperaChromium(
//            listOf(
//                    "operadriver.exe",
//                    "operadriver"
//            ),
//            "webdriver.opera.driver"
//    ),

//    Marionette(
//            listOf(
//                    "wires",
//                    "wires.exe",
//                    "geckodriver",
//                    "geckodriver.exe"
//            ),
//            "webdriver.gecko.driver"
//    ),

//    Edge(
//            listOf(
//                    "MicrosoftWebDriver.exe"
//            ),
//            "webdriver.cell.driver"
//    ),

    Firefox(
            listOf(
//                    "*",
//                    "firefox.exe",
//                    "firefox"
                    "geckodriver.exe",
                    "geckodriver"
            ),
//            "webdriver.firefox.bin"
            "webdriver.gecko.driver"
    );
}