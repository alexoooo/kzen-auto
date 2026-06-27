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
}
