package tech.kzen.auto.client.objects.document.common.signature


/**
 * The selectable simple types for a Script's signature pickers (matches FormulaStep's inferrable class set).
 * Generic element types default to Any until a nested-type picker exists; registered object types are a
 * follow-up. Shared by [LogicSignatureEditor] (parameters) and [ResultSignatureEditor] (result).
 */
object LogicTypeOptions {
    // (qualified class name -> simple label)
    val classOptions: List<Pair<String, String>> = listOf(
        "kotlin.Any" to "Any",
        "kotlin.String" to "String",
        "kotlin.Int" to "Int",
        "kotlin.Long" to "Long",
        "kotlin.Double" to "Double",
        "kotlin.Boolean" to "Boolean",
        "kotlin.collections.List" to "List",
        "kotlin.collections.Set" to "Set")

    val simpleLabelByClassName: Map<String, String> = classOptions.toMap()


    // The user-facing label for a type: the registered simple name (falling back to the class' simple
    // name) with a trailing `?` when nullable. Shared by the signature editor and the Run step's
    // parameter-type badge so both render types identically.
    fun simpleLabel(className: String, nullable: Boolean): String {
        val simple = simpleLabelByClassName[className] ?: className.substringAfterLast('.')
        return if (nullable) "$simple?" else simple
    }
}
