package tech.kzen.auto.server.objects.job.worker.content

import tech.kzen.auto.server.objects.job.expression.JobCalculatedExpression
import tech.kzen.auto.server.objects.job.expression.JobExpressionCompiler
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.service.compile.KotlinStringTemplate
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


/**
 * Where `Write` puts an output below its directory: an optional sub-folder ([folder], blank for none) and a file
 * [name], each the body of a Kotlin raw string template. The templates see what Filter's `where` sees of the
 * written value (its metadata by bare name, such as `name`, `group` or `parent.name`) and the Job's parameters,
 * beside Write's own values ([compressionBinding], [extensionBinding], [timeBinding]), which take the place of a
 * Job parameter of the same name.
 *
 * The parts come from the Kotlin parser ([JobExpressionCompiler.rawStringTemplate]). Text typed literally is checked
 * before Run: none of `\ : * ? " < > |` or a control character, and no `/` in the name, since folders go in the
 * sub-folder, where `/` separates them. Each inserted value is made safe as it is computed, each of those
 * characters and `/` replaced with `_`, so a value never makes a folder or leaves the directory. Each folder and
 * the name then lose trailing dots and spaces, and one whose stem is a Windows device name (`CON`, `NUL`, `COM1`,
 * …, in any case) gets a `_` prefix; one left empty (`.` and `..` included) fails the run by name. The same rule on
 * every OS, so a Job written on one runs on another.
 */
class WritePathTemplate(
    folder: String,
    name: String,
    private val compiler: JobExpressionCompiler
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val folderLabel = "Sub-folder"
        const val nameLabel = "File name"

        const val compressionBinding = "compression"
        const val extensionBinding = "extension"
        const val timeBinding = "time"

        private val text = DataContract(DataType.Scalar(ScalarKind.Text))
        private val bindings = listOf(compressionBinding, extensionBinding, timeBinding)
            .map { BindingDefinition(BindingName(it), text) }
        private val bindingNames = bindings.map { it.name }.toSet()

        // Beside '/', which only a literal in the sub-folder may hold
        private const val unsafe = "\\:*?\"<>|"
        private const val separator = '/'

        private val deviceNames = setOf("CON", "PRN", "AUX", "NUL") +
            (1..9).flatMap { listOf("COM$it", "LPT$it") }

        private val insertedCall = "${WritePathTemplate::class.qualifiedName}.${::inserted.name}"


        /** The text of a value inserted into a path, made safe (called by the compiled templates). */
        @JvmStatic
        fun inserted(value: Any?): String {
            val text = value.toString()
            if (text.none(::isUnsafe)) {
                return text
            }
            return buildString(text.length) {
                for (c in text) {
                    append(if (isUnsafe(c)) '_' else c)
                }
            }
        }


        private fun isUnsafe(c: Char): Boolean =
            c == separator || c in unsafe || c.isISOControl()
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The templates compiled for one input contract. */
    class Compiled(
        private val parameters: BindingSchema,
        private val folder: JobCalculatedExpression<Any?>?,
        private val name: JobCalculatedExpression<Any?>,
        val warnings: List<String>
    ) {
        /**
         * The path of [element]'s output relative to the directory, `/`-separated, with the Job's parameters read
         * through [jobParameter] and Write's own values given.
         */
        fun path(
            element: DataValue,
            jobParameter: (String) -> Any?,
            compression: String,
            extension: String,
            time: String
        ): String {
            val values = parameters.definitions.map {
                when (it.name.value) {
                    compressionBinding -> compression
                    extensionBinding -> extension
                    timeBinding -> time
                    else -> jobParameter(it.name.value)
                }
            }
            val native = JobDataValues.native(element)
            val segments = mutableListOf<String>()
            if (folder != null) {
                folder.setParameters(values)
                val resolved = folder.evaluate(native, element, null).toString()
                resolved.split(separator).mapTo(segments) { segment(folderLabel, "folder", resolved, it) }
            }
            name.setParameters(values)
            val resolved = name.evaluate(native, element, null).toString()
            segments += segment(nameLabel, "file", resolved, resolved)
            return segments.joinToString(separator.toString())
        }


        private fun segment(label: String, noun: String, resolved: String, segment: String): String {
            val trimmed = segment.trimEnd('.', ' ')
            require(trimmed.isNotEmpty()) {
                "$label resolved to '$resolved'; '$segment' cannot name a $noun"
            }
            return if (trimmed.substringBefore('.').uppercase() in deviceNames) "_$trimmed" else trimmed
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val folderCode = folder.takeIf { it.isNotBlank() }?.let { code(folderLabel, it, folders = true) }
    private val nameCode = code(nameLabel, name, folders = false)


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Checks the templates before Run against a lane of [contract]: compiled when the whole contract is known, or
     * else only parsed (done when this was made), since a field of a dynamic part exists only once values do.
     * Returns the compiler's warnings; fails with the first error, named by its field.
     */
    fun check(contract: DataContract, job: BindingSchema, classLoader: ClassLoader): List<String> =
        if (known(contract.payload()) && contract.metadata.let { it == null || known(it.contract) }) {
            compile(contract, job, classLoader).warnings
        }
        else {
            listOf()
        }


    /** Compiles the templates over values of [contract]; fails with the first error, named by its field. */
    fun compile(contract: DataContract, job: BindingSchema, classLoader: ClassLoader): Compiled {
        // The Job's parameters, but for those Write's own values replace, then Write's own
        val parameters = BindingSchema.of(job.definitions.filter { it.name !in bindingNames } + bindings)
        val folder = folderCode?.let { compile(folderLabel, it, contract, parameters, classLoader) }
        val name = compile(nameLabel, nameCode, contract, parameters, classLoader)
        return Compiled(
            parameters,
            folder?.compiled?.expression,
            checkNotNull(name.compiled).expression,
            folder?.warnings.orEmpty() + name.warnings)
    }


    private fun compile(
        label: String,
        code: String,
        contract: DataContract,
        parameters: BindingSchema,
        classLoader: ClassLoader
    ): JobExpressionCompiler.Attempt {
        val attempt = compiler.compile(
            "write_${label.filter(Char::isLetter)}", code, contract, TypeMetadata.anyNullable, classLoader, parameters)
        require(attempt.error == null) { "$label: ${attempt.error}" }
        return attempt
    }


    // Each named definition is looked into once, so a recursive record ends
    private fun known(contract: DataContract, seen: MutableSet<DefinitionId> = mutableSetOf()): Boolean {
        val reference = contract.structural as? DataType.Reference
        if (reference != null && !seen.add(reference.id)) {
            return true
        }
        return when (val type = contract.expanded().structural) {
            is DataType.Dynamic -> false
            is DataType.Record -> type.fields.all { known(contract.child(DataPathSegment.Field(it.id)), seen) }
            else -> true
        }
    }


    /** The Kotlin expression of the template [body]: a raw string whose inserted values pass through [inserted]. */
    private fun code(label: String, body: String, folders: Boolean): String {
        val template =
            try {
                compiler.rawStringTemplate(body)
            }
            catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$label: ${e.message}", e)
            }
        val code = StringBuilder("\"\"\"")
        for (part in template.parts) {
            when (part) {
                is KotlinStringTemplate.Part.Literal -> {
                    checkLiteral(label, part.text, folders)
                    code.append(part.text)
                }
                is KotlinStringTemplate.Part.Inserted ->
                    code.append("\${").append(insertedCall).append('(').append(part.expression).append(")}")
            }
        }
        return code.append("\"\"\"").toString()
    }


    private fun checkLiteral(label: String, literal: String, folders: Boolean) {
        for (c in literal) {
            require(c != separator || folders) {
                "$label: '/' would make a folder; put folders in $folderLabel"
            }
            require(c == separator || !isUnsafe(c)) {
                if (c.isISOControl()) "$label: a control character (U+%04X) cannot be in a path".format(c.code)
                else "$label: '$c' cannot be in a path"
            }
        }
    }
}
