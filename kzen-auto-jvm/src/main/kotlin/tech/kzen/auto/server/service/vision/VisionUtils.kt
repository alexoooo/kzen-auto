package tech.kzen.auto.server.service.vision


object VisionUtils {
    fun xpathEscape(value: String): String {
        // https://stackoverflow.com/a/38254661/1941359
        return when {
            "'" !in value ->
                "'$value'"

            "\"" !in value ->
                '"' + value + '"'

            else ->
                "concat('${
                    value.replace("'", """',"'",'""")
                }')"
        }
    }
}
