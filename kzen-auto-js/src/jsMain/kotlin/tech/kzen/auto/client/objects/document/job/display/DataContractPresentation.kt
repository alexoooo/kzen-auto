package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.lib.common.exec.data.shape.DataShape
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataConstraint
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.renderName
import tech.kzen.lib.platform.ClassNames.simple
import web.cssom.Color


internal object DataContractPresentation {
    data class Presentation(
        val summary: String,
        val title: String,
        val details: List<String>,
        val color: Color
    )

    private const val maxInlineSymbols = 6

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
        DataContractDisplay.Reading -> Presentation(
            "Reading data…", "Reading data before Run; the type follows", emptyList(), muted)
        is DataContractDisplay.Contract -> contract(display.contract, display.shape, display.provenance)
    }

    private fun contract(contract: DataContract, shape: DataShape?, provenance: String?): Presentation {
        val details = mutableListOf<String>()
        contract.nativeByPath.entries.sortedBy { it.key.toString() }.forEach { (path, metadata) ->
            details.add("JVM $path: ${metadata.className.asString()}")
        }
        contract.constraintsByPath.entries.sortedBy { it.key.toString() }.forEach { (path, constraints) ->
            constraints.forEach { details.add("$path ${constraint(it)}") }
        }
        if (shape != null) {
            details.add("provenance: ${shape.provenance.name}")
            details.add("stability: ${stability(shape.stability)}")
            shape.diagnostics.forEach { diagnostic ->
                val location = diagnostic.location?.let { " at $it" }.orEmpty()
                details.add("${diagnostic.severity.name}: ${diagnostic.code}$location — ${diagnostic.message}")
            }
        }
        val metadataFields = contract.metadata?.structural?.fields.orEmpty()
        val summary = typeLabel(contract) +
            (if (metadataFields.isEmpty()) "" else " + ${metadataFields.size} metadata") +
            (if (provenance == null) "" else " · inferred")
        val title = typeTitle(contract) +
            (if (metadataFields.isEmpty()) "" else " · metadata: ${metadataFields.joinToString { it.id.name }}") +
            (provenance?.let { " · $it" } ?: "")
        return Presentation(summary, title, details, normal)
    }

    fun typeLabel(contract: DataContract): String {
        val type = contract.structural
        val native = contract.nativeByPath[DataTypePath.root]
        val label = when (type) {
            is DataType.Record -> native?.let { "${it.toSimple()} (Record)" } ?: "Record"
            is DataType.Union -> "Union"
            // An opaque value is named by what it is (`Content`); "Opaque" stays in the title
            is DataType.Opaque -> native?.className?.simple() ?: summary(type)
            else -> summary(type)
        }
        val symbols = symbolSet(contract)?.let { " ∈ {${abbreviated(it.symbols)}}" }.orEmpty()
        return label + (if (type.nullable) "?" else "") + symbols
    }

    fun typeTitle(contract: DataContract): String = buildString {
        append(summary(contract.structural))
        if (contract.structural.nullable) append(" · nullable")
        symbolSet(contract)?.let { append(" · one of: ${it.symbols.joinToString()}") }
        contract.nativeByPath[DataTypePath.root]?.let {
            append(" · JVM: ${it.className.asString()}")
        }
    }

    private fun symbolSet(contract: DataContract): DataConstraint.SymbolSet? =
        contract.constraintsByPath[DataTypePath.root]?.firstNotNullOfOrNull { it as? DataConstraint.SymbolSet }

    // Inline labels stay short; the title carries the full set
    private fun abbreviated(symbols: List<String>): String =
        if (symbols.size <= maxInlineSymbols) symbols.joinToString()
        else symbols.take(maxInlineSymbols - 1).joinToString() + ", …"

    private fun constraint(constraint: DataConstraint): String = when (constraint) {
        is DataConstraint.SymbolSet -> "one of: ${constraint.symbols.joinToString()}"
    }

    fun summary(type: DataType): String = when (type) {
        is DataType.Record -> "Record · ${type.fields.size} ${if (type.fields.size == 1) "field" else "fields"}"
        is DataType.Dynamic -> "Dynamic"
        is DataType.Scalar -> scalar(type.kind)
        is DataType.Listing -> "List"
        is DataType.Mapping -> "Map"
        is DataType.Union -> "Union · ${type.variants.size} variants"
        is DataType.Opaque -> "Opaque"
        is DataType.Reference -> "↻ ${type.id.value}"
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
