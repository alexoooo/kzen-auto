package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataVariant
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.NativeTypeToken
import tech.kzen.lib.common.exec.data.type.ResolvedDataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


class JobLaneDescriptorTest {
    private data class Reading(val value: Double)

    @Test
    fun canonicalDescriptorProjectsLegacyAuthoringViews() {
        val flat = JobLaneDescriptor(null, HeaderListing.of(listOf("x", "x")))
        assertEquals(listOf(FieldId("x", 0), FieldId("x", 1)),
            (flat.contract.structural as DataType.Record).fields.map { it.id })
        assertEquals(listOf("x", "x (2)"), flat.flatColumns?.values?.map { it.render() })
        assertNull(flat.payloadType)

        val scalar = JobLaneDescriptor(TypeMetadata.double, HeaderListing.empty)
        assertEquals(DataType.Scalar(ScalarKind.Floating(64)), scalar.contract.structural)
        assertEquals(TypeMetadata.double, scalar.payloadType)
        assertEquals(listOf("value"), scalar.consumerFlatColumns()?.values?.map { it.render() })

        val mappingType = TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(TypeMetadata.string, TypeMetadata.int),
            false)
        val mapping = JobLaneDescriptor(mappingType, HeaderListing.empty)
        assertEquals(DataType.Mapping(
            DataType.Scalar(ScalarKind.Text),
            DataType.Scalar(ScalarKind.Integer(32))),
            mapping.contract.structural)
        assertNull(mapping.consumerFlatColumns())
        assertNull(JobLaneDescriptor.unknown.consumerFlatColumns())
    }

    @Test
    fun widenedRecordCarriesNativeRequirementWithoutASecondHeaderAuthority() {
        val readingType = TypeMetadata(
            ClassName(Reading::class.qualifiedName!!), emptyList(), false)
        val contract = DataContract(
            DataType.Record(listOf(
                DataField(FieldId("value"), DataType.Scalar(ScalarKind.Floating(64))),
                DataField(FieldId("normalized"), DataType.Scalar(ScalarKind.Text)))),
            mapOf(DataTypePath.root to readingType))
        val lane = JobLaneDescriptor(contract)

        assertEquals(readingType, lane.payloadType)
        assertEquals(listOf("value", "normalized"), lane.flatColumns?.values?.map { it.render() })
        assertEquals(lane.flatColumns, lane.consumerFlatColumns())
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun structuralDeclarationAndResolvedKeysChangeAtTheirOwnLayer() {
        val structural = DataType.Record(listOf(
            DataField(FieldId("value"), DataType.Scalar(ScalarKind.Floating(64)))))
        val firstMetadata = TypeMetadata(
            ClassName(Reading::class.qualifiedName!!), emptyList(), false)
        val secondMetadata = TypeMetadata(
            ClassName("example.OtherReading"), emptyList(), false)
        val first = DataContract(structural, mapOf(DataTypePath.root to firstMetadata))
        val second = DataContract(structural, mapOf(DataTypePath.root to secondMetadata))

        val declarationA = JobLaneDescriptor(first)
        val declarationB = JobLaneDescriptor(second)
        assertEquals(declarationA.structuralKey, declarationB.structuralKey)
        assertNotEquals(declarationA.declarationKey, declarationB.declarationKey)

        val resolved = ResolvedDataContract(
            first,
            mapOf(DataTypePath.root to NativeTypeToken(typeOf<Reading>())))
        val resolvedLane = JobLaneDescriptor(first, resolved)
        assertEquals(declarationA.declarationKey, resolvedLane.declarationKey)
        assertNotEquals(declarationA.resolvedKey, resolvedLane.resolvedKey)
    }

    @Test
    fun unionAndDynamicRemainNonProjectableUntilSettled() {
        val union = DataContract(DataType.Union(listOf(
            DataVariant(VariantId("text"), DataType.Scalar(ScalarKind.Text)),
            DataVariant(VariantId("number"), DataType.Scalar(ScalarKind.Integer(64))))))
        assertNull(JobLaneDescriptor(union).consumerFlatColumns())
        assertNull(JobLaneDescriptor.unknown.payloadType)
    }
}
