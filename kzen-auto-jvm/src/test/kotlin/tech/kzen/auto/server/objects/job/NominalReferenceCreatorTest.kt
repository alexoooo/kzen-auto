package tech.kzen.auto.server.objects.job

import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphCreator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


/** Pins the custom creator's only contract: nominal references remain values instead of graph dependencies. */
class NominalReferenceCreatorTest {
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun blankSameDocumentCrossDocumentDanglingAndWrongTypeReferencesArePreserved() {
        context = KzenAutoContext.forTest()
        val documentPath = DocumentPath.parse("test/job/reference/nominal-reference-test.yaml")
        val definition = AutoTestUtils
            .graphDefinitionAttempt(AutoTestUtils.readNotation())
            .transitiveSuccessful
        val instance = GraphCreator.createGraph(
            definition.filterTransitive(documentPath), context.graphEnvironment)

        assertNull(holder(instance, documentPath, "BlankHolder").reference)
        assertEquals(
            ObjectReference.parse("SameTarget"),
            holder(instance, documentPath, "SameHolder").reference)
        assertEquals(
            ObjectReference.parse(
                "test/job/reference/nominal-reference-target-test.yaml#CrossTarget"),
            holder(instance, documentPath, "CrossHolder").reference)
        assertEquals(
            ObjectReference.parse("MissingTarget"),
            holder(instance, documentPath, "DanglingHolder").reference)
        assertEquals(
            ObjectReference.parse("NominalReferenceCreator"),
            holder(instance, documentPath, "WrongTypeHolder").reference)
    }


    private fun holder(
        instance: GraphInstance,
        documentPath: DocumentPath,
        name: String
    ): NominalReferenceHolder {
        val location = ObjectLocation(documentPath, ObjectPath.parse(name))
        return instance[location]?.reference as NominalReferenceHolder
    }
}
