package tech.kzen.auto.common.objects.document.custom.create

import kotlin.test.Test
import kotlin.test.assertEquals
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


class SharedCustomDocumentTest {
    @Test
    fun createsAndExportsConfigurationOnlyObject() {
        val prototype = ObjectLocation.parse("types.yaml#ConfiguredFormat")
        val body = ObjectNotation.ofParent(prototype.toReference())
            .upsertAttribute(AttributeName("delimiter"), ScalarAttributeNotation(";"))
        val creation = CustomCreation(prototype, "Formats", "Delimited format", body)

        val document = SharedCustomDocument.create(
            ObjectLocation.parse("types.yaml#CustomDocument"), creation)
        val createdPath = SharedCustomDocument.objectPath(creation)

        assertEquals(body, document.notations.map[createdPath])
        val exports = document.notations.map.getValue(NotationConventions.mainObjectPath)
            .get(CustomConventions.exportsListAttributeName) as ListAttributeNotation
        assertEquals(createdPath.asString(), (exports.values.single() as ScalarAttributeNotation).value)
    }
}
