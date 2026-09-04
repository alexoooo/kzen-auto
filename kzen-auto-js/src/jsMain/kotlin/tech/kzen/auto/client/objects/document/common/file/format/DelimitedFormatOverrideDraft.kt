package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.common.data.format.DelimitedFormatOverrideConventions
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue


data class DelimitedFormatOverrideDraft(
    val delimiter: String,
    val firstRowHeader: Boolean,
    val encoding: String,
    val skipLeadingLines: String,
    val commentPrefix: String
) {
    val error: String?
        get() = when {
            delimiter.length != 1 -> "Delimiter must be exactly one character."
            encoding.isBlank() -> "Encoding is required."
            skipLeadingLines.toIntOrNull()?.let { it >= 0 } != true ->
                "Lines to skip must be a nonnegative whole number."
            else -> null
        }

    fun overrides(): Map<String, String?> {
        check(error == null) { error.orEmpty() }
        return linkedMapOf(
            DelimitedFormatOverrideConventions.delimiter to delimiter,
            DelimitedFormatOverrideConventions.header to
                if (firstRowHeader) headerPresent else headerInferLabels,
            DelimitedFormatOverrideConventions.encoding to encoding,
            DelimitedFormatOverrideConventions.skipLeadingLines to skipLeadingLines.toInt().toString(),
            DelimitedFormatOverrideConventions.commentPrefix to commentPrefix.takeIf(String::isNotEmpty))
    }

    fun headerExplanation(): String {
        val skipped = skipLeadingLines.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        val prefix = if (skipped == 0) "" else "After skipping $skipped leading line${if (skipped == 1) "" else "s"}, "
        return if (firstRowHeader) {
            prefix + "the next row supplies the column names."
        }
        else {
            prefix + "the next row remains data and columns use positional names."
        }
    }

    companion object {
        const val headerPresent = "present"
        const val headerInferLabels = "infer-labels"

        fun of(config: MapExecutionValue): DelimitedFormatOverrideDraft {
            val dialect = config.values["dialect"] as? MapExecutionValue
                ?: throw IllegalArgumentException("Delimited format is missing its dialect")
            val header = config.values["header"] as? MapExecutionValue
                ?: throw IllegalArgumentException("Delimited format is missing its header policy")
            val characters = config.values["characters"] as? MapExecutionValue
                ?: throw IllegalArgumentException("Delimited format is missing its character settings")
            return DelimitedFormatOverrideDraft(
                (dialect.values["delimiter"] as? TextExecutionValue)?.value
                    ?: throw IllegalArgumentException("Delimited format is missing its delimiter"),
                (header.values["policy"] as? TextExecutionValue)?.value == headerPresent,
                (characters.values["charset"] as? TextExecutionValue)?.value
                    ?: throw IllegalArgumentException("Delimited format is missing its encoding"),
                ((config.values["skipLeadingLines"] as? LongExecutionValue)?.value ?: 0).toString(),
                (config.values["commentPrefix"] as? TextExecutionValue)?.value.orEmpty())
        }
    }
}
