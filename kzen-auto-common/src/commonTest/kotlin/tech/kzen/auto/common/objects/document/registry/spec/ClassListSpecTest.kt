package tech.kzen.auto.common.objects.document.registry.spec

import tech.kzen.auto.common.objects.document.registry.ObjectRegistryConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveListItemInAttributeCommand
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


/**
 * Pins the ObjectRegistry class-list notation shape: what a registry document parses into, and the commands the
 * editor emits to add/remove an entry. There is no unparse — the list is written by the commands, so the command
 * shapes are the other half of the round trip.
 */
class ClassListSpecTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainLocation = ObjectLocation(
        DocumentPath.parse("test/registry-spec-test.yaml"), ObjectPath.parse("main"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parsePopulatedList() {
        val notation = ListAttributeNotation(persistentListOf(
            ScalarAttributeNotation("kotlin.ranges.IntRange"),
            ScalarAttributeNotation("kotlin.ranges.CharRange")))

        assertEquals(
            ClassListSpec(listOf(
                ClassName("kotlin.ranges.IntRange"),
                ClassName("kotlin.ranges.CharRange"))),
            ClassListSpec.ofAttributeNotation(notation))
    }


    @Test
    fun parseEmptyList() {
        assertEquals(
            ClassListSpec(listOf()),
            ClassListSpec.ofAttributeNotation(ListAttributeNotation.empty))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun addCommandAppendsScalarToClassesAttribute() {
        val command = assertIs<InsertListItemInAttributeCommand>(
            ClassListSpec.addCommand(mainLocation, ClassName("kotlin.ranges.CharRange")))

        assertEquals(mainLocation, command.objectLocation)
        assertEquals(ObjectRegistryConventions.classesAttributePath, command.containingList)
        assertEquals(PositionRelation.afterLast, command.indexInList)
        assertEquals(ScalarAttributeNotation("kotlin.ranges.CharRange"), command.item)
    }


    @Test
    fun removeCommandTargetsMatchingScalar() {
        val command = assertIs<RemoveListItemInAttributeCommand>(
            ClassListSpec.removeCommand(mainLocation, ClassName("kotlin.ranges.IntRange")))

        assertEquals(mainLocation, command.objectLocation)
        assertEquals(ObjectRegistryConventions.classesAttributePath, command.containingList)
        assertEquals(ScalarAttributeNotation("kotlin.ranges.IntRange"), command.item)
    }
}
