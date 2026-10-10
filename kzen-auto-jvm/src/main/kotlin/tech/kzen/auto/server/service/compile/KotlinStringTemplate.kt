package tech.kzen.auto.server.service.compile


/**
 * A Kotlin string template as the Kotlin parser reads it ([KotlinSyntaxValidator.rawStringTemplate]): its [parts]
 * in order, each text typed literally or an expression whose value is inserted (`$name` or `${…}`).
 */
class KotlinStringTemplate(
    val parts: List<Part>
) {
    sealed interface Part {
        class Literal(val text: String): Part
        class Inserted(val expression: String): Part
    }
}
