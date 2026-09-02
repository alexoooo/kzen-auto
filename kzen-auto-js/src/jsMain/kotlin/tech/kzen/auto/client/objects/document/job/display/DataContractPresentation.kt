package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.lib.common.exec.data.shape.DataShape
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.renderName
import web.cssom.Color


internal object DataContractPresentation {
    data class Presentation(
        val summary: String,
        val title: String,
        val details: List<String>,
        val color: Color
    )

    private val normal = Color("rgba(0, 0, 0, 0.65)")
    private val muted = Color("rgba(0, 0, 0, 0.48)")
    private val error = Color("#c62828")
    private val dynamic = Color("#8a5a00")

    fun of(display: DataContractDisplay): Presentation = when (display) {
        DataContractDisplay.Loading -> Presentation("Loading…", "Contract loading", emptyList(), muted)
        DataContractDisplay.Unavailable -> Presentation(
            "Unavailable", "No contract is available", emptyList(), muted)
        is DataContractDisplay.Error -> Presentation(
            "Error", display.message, listOf(display.message), error)
        DataContractDisplay.Dynamic -> Presentation(
            "Dynamic", "Runtime-keyed dynamic contract", emptyList(), dynamic)
        is DataContractDisplay.Contract -> contract(display.contract, display.shape)
    }

    private fun contract(contract: DataContract, shape: DataShape?): Presentation {
        val details = mutableListOf<String>()
        appendType(details, contract.structural, "", "item")
        contract.nativeByPath.entries.sortedBy { it.key.toString() }.forEach { (path, metadata) ->
            details.add("native $path: ${metadata.toSimple()}")
        }
        if (shape != null) {
            details.add("provenance: ${shape.provenance.name}")
            details.add("stability: ${stability(shape.stability)}")
            shape.diagnostics.forEach { diagnostic ->
                val location = diagnostic.location?.let { " at $it" }.orEmpty()
                details.add("${diagnostic.severity.name}: ${diagnostic.code}$location — ${diagnostic.message}")
            }
        }
        return Presentation(summary(contract.structural), "Contract details", details, normal)
    }

    private fun summary(type: DataType): String = when (type) {
        is DataType.Record -> "Record · ${type.fields.size} ${if (type.fields.size == 1) "field" else "fields"}"
        is DataType.Dynamic -> "Dynamic"
        is DataType.Scalar -> scalar(type.kind)
        is DataType.Listing -> "List"
        is DataType.Mapping -> "Map"
        is DataType.Union -> "Union · ${type.variants.size} variants"
        is DataType.Opaque -> "Opaque"
    }

    private fun appendType(lines: MutableList<String>, type: DataType, indent: String, label: String) {
        val nullable = if (type.nullable) " · nullable" else ""
        when (type) {
            is DataType.Record -> {
                lines.add("$indent$label: Record$nullable")
                type.fields.forEach { appendField(lines, it, "$indent  ") }
            }
            is DataType.Listing -> {
                lines.add("$indent$label: List$nullable")
                appendType(lines, type.element, "$indent  ", "element")
            }
            is DataType.Mapping -> {
                lines.add("$indent$label: Map$nullable")
                appendType(lines, type.key, "$indent  ", "key")
                appendType(lines, type.value, "$indent  ", "value")
            }
            is DataType.Union -> {
                lines.add("$indent$label: Union$nullable")
                type.variants.forEach { appendType(lines, it.type, "$indent  ", it.id.value) }
            }
            is DataType.Scalar -> lines.add("$indent$label: ${scalar(type.kind)}$nullable")
            is DataType.Dynamic -> lines.add("$indent$label: Dynamic$nullable")
            is DataType.Opaque -> lines.add("$indent$label: Opaque$nullable")
        }
    }

    private fun appendField(lines: MutableList<String>, field: DataField, indent: String) {
        val occurrence = if (field.id.occurrence == 0) "" else "#${field.id.occurrence}"
        val optional = if (field.optional) " · optional" else ""
        val before = lines.size
        appendType(lines, field.type, indent, field.id.name + occurrence)
        lines[before] = lines[before] + optional
    }

    private fun scalar(kind: ScalarKind): String =
        kind.renderName().replaceFirstChar { it.uppercase() }

    private fun stability(stability: ShapeStability): String = when (stability) {
        ShapeStability.Stable -> "Stable"
        is ShapeStability.Provisional -> buildString {
            append("Provisional · ${stability.coverage.observedItems} items")
            stability.coverage.observedBytes?.let { append(" · $it bytes") }
            append(if (stability.coverage.complete) " · complete" else " · partial")
        }
    }
}
