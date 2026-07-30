package tech.kzen.auto.common.objects.document.custom.model

import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * The Builder's reference-return discipline is what keeps the Custom store's per-notation-event recompute
 * publish-free and lets RPureComponent bail for untouched object cards. Pinned here (a common class exercised
 * from the jvm module, as ScriptDependencyAnalysisTest does) so a "simplification" to always-fresh instances
 * can't land silently.
 */
class CustomViewModelBuilderTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/custom/custom-view-model-test.yaml")
    private val elsewherePath = DocumentPath.parse("test/custom/custom-prototype-elsewhere-test.yaml")

    private val greeting = ObjectPath.parse("main.objects/Greeting")
    private val other = ObjectPath.parse("main.objects/Other")

    private val prototypeLocation = ObjectLocation(documentPath, ObjectPath.parse("ViewModelPrototype"))
    private val candidateLocation = ObjectLocation(elsewherePath, ObjectPath.parse("Candidate"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun unchangedInputsReturnSameInstance() {
        val builder = CustomViewModel.Builder()
        val notation = AutoTestUtils.readNotation()

        val first = update(builder, notation)
        val second = update(builder, notation)

        assertSame(first, second)
    }


    @Test
    fun modelListsDocumentObjectsWithoutMain() {
        val model = update(CustomViewModel.Builder(), AutoTestUtils.readNotation())

        assertContains(model.orderedEntries.map { it.objectLocation.objectPath }, greeting)
        assertContains(model.orderedEntries.map { it.objectLocation.objectPath }, other)
        assertTrue(model.orderedEntries.none { it.objectLocation.objectPath == ObjectPath.parse("main") })

        val greetingEntry = model.orderedEntries.single { it.objectLocation.objectPath == greeting }
        assertTrue(greetingEntry.info.isExported, "the fixture exports Greeting")
        assertTrue(greetingEntry.info.isDetached)
        assertTrue(greetingEntry.info.isLogic)

        val otherEntry = model.orderedEntries.single { it.objectLocation.objectPath == other }
        assertTrue(!otherEntry.info.isExported, "the fixture does not export Other")
    }


    @Test
    fun unrelatedObjectEditReusesSiblingEntries() {
        val builder = CustomViewModel.Builder()
        val baseNotation = AutoTestUtils.readNotation()

        val first = update(builder, baseNotation)
        val second = update(builder, edit(
            baseNotation,
            ObjectLocation(documentPath, other),
            "abstract",
            ScalarAttributeNotation("true")))

        assertNotSame(first, second, "the edited object's derived info changed, so the model is new")
        assertSame(
            first.orderedEntries.single { it.objectLocation.objectPath == greeting },
            second.orderedEntries.single { it.objectLocation.objectPath == greeting },
            "the untouched object's entry must stay reference-stable")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun prototypesListedAndStableAcrossNoOp() {
        val builder = CustomViewModel.Builder()
        val notation = AutoTestUtils.readNotation()

        val first = update(builder, notation)
        assertContains(first.prototypes, prototypeLocation)

        assertSame(first, update(builder, notation))
    }


    @Test
    fun prototypeAddedElsewhereChangesModel() {
        // The whole point of running the Builder per notation event rather than per this document's own state
        // change: nothing in test/custom/custom-view-model-test.yaml moved, yet the picker must pick this up.
        val builder = CustomViewModel.Builder()
        val baseNotation = AutoTestUtils.readNotation()

        val first = update(builder, baseNotation)
        assertTrue(candidateLocation !in first.prototypes)

        val second = update(builder, edit(
            baseNotation, candidateLocation, "is", ScalarAttributeNotation("Prototype")))

        assertNotSame(first, second)
        assertContains(second.prototypes, candidateLocation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun update(builder: CustomViewModel.Builder, graphNotation: GraphNotation): CustomViewModel {
        val graphStructure = GraphStructure(graphNotation, AutoTestUtils.graphMetadata(graphNotation))
        val serverNotation = graphNotation.documents[documentPath]!!.objects
        return builder.update(documentPath, serverNotation, graphStructure)
    }


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
