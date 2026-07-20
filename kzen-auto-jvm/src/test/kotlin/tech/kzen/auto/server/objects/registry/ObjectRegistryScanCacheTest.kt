package tech.kzen.auto.server.objects.registry

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame


/**
 * [ObjectRegistryDocument.scan] reflects every registered class name on every call, and rides the
 * ScriptValidationCache's misses — so it must be memoized by the registry content itself, and must invalidate
 * on exactly a registry edit. `assertSame` across equal-but-distinct notation instances is the point: the key
 * is the registry-content digest, not identity.
 */
class ObjectRegistryScanCacheTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val registryDocument = DocumentPath.parse("auto-jvm/registry/registry-jvm.yaml")
    private val unrelatedDocument = DocumentPath.parse("test/script-engine-if-test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun repeatedScanReturnsCachedInstance() {
        val first = ObjectRegistryDocument.scan(AutoTestUtils.readNotation())
        val second = ObjectRegistryDocument.scan(AutoTestUtils.readNotation())

        assertSame(first, second)
    }


    @Test
    fun registryEditRecomputes() {
        val baseNotation = AutoTestUtils.readNotation()
        val first = ObjectRegistryDocument.scan(baseNotation)

        val edited = edit(
            baseNotation,
            ObjectLocation(registryDocument, ObjectPath.parse("main")),
            "classes",
            ListAttributeNotation(persistentListOf(
                ScalarAttributeNotation("kotlin.ranges.IntRange"),
                ScalarAttributeNotation("kotlin.ranges.CharRange"))))
        val second = ObjectRegistryDocument.scan(edited)

        assertNotSame(first, second)
        assertContains(second.classNames, ClassName("kotlin.ranges.CharRange"))
    }


    @Test
    fun unrelatedEditStaysCached() {
        val baseNotation = AutoTestUtils.readNotation()
        val first = ObjectRegistryDocument.scan(baseNotation)

        val edited = edit(
            baseNotation,
            ObjectLocation(unrelatedDocument, ObjectPath.parse("main.steps/Flag")),
            "code",
            ScalarAttributeNotation("false"))

        assertSame(first, ObjectRegistryDocument.scan(edited))
    }


    @Test
    fun unresolvableClassStillFilteredThroughCache() {
        val edited = edit(
            AutoTestUtils.readNotation(),
            ObjectLocation(registryDocument, ObjectPath.parse("main")),
            "classes",
            ListAttributeNotation(persistentListOf(
                ScalarAttributeNotation("kotlin.ranges.IntRange"),
                ScalarAttributeNotation("no.such.Class"))))

        val scan = ObjectRegistryDocument.scan(edited)

        assertContains(scan.classNames, ClassName("kotlin.ranges.IntRange"))
        assertFalse(ClassName("no.such.Class") in scan.classNames, "unresolvable class must not survive the cache")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        attribute: String,
        value: AttributeNotation
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(location, AttributeName(attribute), value))
            .graphNotation
    }
}
