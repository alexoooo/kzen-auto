package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.StructuralNotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphStore


object CommonEditUtils {
    fun editCommand(
        objectLocation: ObjectLocation,
        attributePath: AttributePath,
        attributeNotation: AttributeNotation
    ):
            StructuralNotationCommand
    {
        return when {
            attributePath.nesting.segments.isEmpty() ->
                UpsertAttributeCommand(
                    objectLocation,
                    attributePath.attribute,
                    attributeNotation)

            else ->
                UpdateInAttributeCommand(
                    objectLocation,
                    attributePath,
                    attributeNotation)
        }
    }


    // Apply [command], returning null on success or the field-local error message on failure. The message falls
    // back to toString() because Throwable.message can be null and a null return means success. The failure also
    // reaches the global error banner via the store's own publishFailure — this is additive field feedback, so no
    // suppressErrorDisplay attachment is passed.
    suspend fun applyCommand(
        graphStore: MirroredGraphStore,
        command: NotationCommand
    ): String? {
        val result = graphStore.apply(command)

        val error = (result as? MirroredGraphError)?.error
            ?: return null

        return error.message ?: error.toString()
    }


    fun formattedLabel(
        attributePath: AttributePath,
        labelOverride: String? = null
    ): String {
        if (labelOverride != null) {
            return labelOverride
        }

        val defaultLabel =
            if (attributePath.nesting.segments.isEmpty()) {
                attributePath.attribute.value
            }
            else {
                attributePath.nesting.segments.last().asString()
            }

        val upperCamelCase = defaultLabel
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val results = Regex("\\w+").findAll(upperCamelCase)
        val words = results.map { it.groups[0]!!.value }

        return words.joinToString(" ")
    }
}