package tech.kzen.auto.server.service.webdriver.model


enum class OperatingSystem(
        private val osIdentifier: String
) {
    Windows("windows"),
    OsX("mac"),
    Linux("linux");


    companion object {
        private fun find(osName: String): OperatingSystem {
            for (operatingSystemName in values()) {
                if (osName.toLowerCase().contains(operatingSystemName.osIdentifier)) {
                    return operatingSystemName
                }
            }

            throw IllegalArgumentException("Unrecognised operating system name '$osName'")
        }


        fun get(): OperatingSystem {
            val currentOperatingSystemName = System.getProperties().getProperty("os.name")

            return find(currentOperatingSystemName)
        }
    }
}