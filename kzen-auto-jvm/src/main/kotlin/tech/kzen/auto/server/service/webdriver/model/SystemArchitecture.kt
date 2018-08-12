package tech.kzen.auto.server.service.webdriver.model


enum class SystemArchitecture(
        val osArchitecture: String
) {
    X86_64("64bit"),
    X86_32("32bit"),
    Arm("arm");


    companion object {
        private val architecture64bitNames = listOf("amd64", "x86_64")
        private val architectureArmNames = listOf("arm", "armv41")


        private fun find(currentArchitecture: String): SystemArchitecture {
            var result = X86_32

            if (architecture64bitNames.contains(currentArchitecture)) {
                result = X86_64
            }

            if (architectureArmNames.contains(currentArchitecture)) {
                result = Arm
            }

            return result
        }


        fun get(): SystemArchitecture {
            val currentArchitecture = System.getProperties().getProperty("os.arch")

            return find(currentArchitecture)
        }
    }
}