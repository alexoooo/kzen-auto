package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.common.objects.document.job.JobChannelSynthesis
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.platform.ClassName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull


/**
 * Unit test for [JobValidator.validate] — the static payload-type walk: lanes fold along the order-driven
 * wiring, each Worker's [tech.kzen.auto.server.objects.job.worker.WorkerBase.payloadFlow] mapping input to
 * output. Covers the typed chain (FormulaSource streams Int -> Formula `payload:` maps Int -> Filter / sink
 * identity — every card would show the Int chip), a broken expression surfacing as that Worker's validation
 * ERROR (not a crash), a nested-Logic RunWorker typed by its Script callee's declared `results` signature, the
 * ResultSink's declared-`results` checks (undeclared component; declared-vs-inferred assignability — mismatch
 * rejected, supertype accepted, nullable-into-non-nullable rejected), and the unknown-column lane, where
 * validation degrades to syntax rather than switching off: a manually-wired CSV pipeline of well-formed
 * expressions yields no false errors, while malformed source on a CSV lane is still caught.
 * Drives the same synthesize + filter + instantiate steps as [JobValidator.execute] (and
 * [tech.kzen.auto.server.exec.job.JobRun]), without the detached/cache layers.
 */
class JobValidatorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun typedPayloadChainInfersEveryLane() {
        val validation = validate("test/job/message/job-payload-formula-test.yaml")

        // Source: (1..3) infers IntRange — an Iterable type, so the lane streams its element type.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/source"))
        // Formula `payload: payload * 10` over an Int receiver infers Int.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/transform"))
        // Filter and sink forward the lane (identity) — their cards show the same type.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/filter"))
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/collect"))

        assertEquals(
            listOf(), validation.workerValidations.values.mapNotNull { it.errorMessage },
            "the whole chain validates clean")
    }


    @Test
    fun firstPartyPayloadTypeStaysConcrete() {
        val validation = validate("test/job/message/job-first-party-payload-type-test.yaml")
        val dataUnit = TypeMetadata(
            ClassName("tech.kzen.auto.common.data.model.DataUnit"), listOf(), false)

        assertEquals(dataUnit, typeOf(validation, "main.workers/source"))
        assertEquals(dataUnit, typeOf(validation, "main.workers/collect"))
        assertEquals(
            listOf(), validation.workerValidations.values.mapNotNull { it.errorMessage },
            "the first-party type is importable by downstream validation")
    }


    @Test
    fun readWorkerValidModesPublishUnitsAndUnknownItems() {
        val items = validate("test/job/run/job-read-test.yaml")
        val itemRead = items.workerValidations[ObjectPath.parse("main.workers/read")]
        assertNotNull(itemRead)
        assertNull(itemRead.errorMessage)
        assertNull(itemRead.typeMetadata, "FileDataSource has no static item shape")
        assertNull(itemRead.flatColumns, "undeclared explicit files stay unknown without design-time IO")

        val units = validate("test/job/run/job-read-units-validation-test.yaml")
        val unitRead = units.workerValidations[ObjectPath.parse("main.workers/read")]
        assertNotNull(unitRead)
        assertNull(unitRead.errorMessage)
        assertEquals(
            DataUnit::class.qualifiedName,
            unitRead.typeMetadata?.className?.asString())

        val readPart = units.workerValidations[ObjectPath.parse("main.workers/readPart")]
        assertNotNull(readPart)
        assertNull(readPart.errorMessage, "Read(units) supplies the required non-null DataUnit lane")
        assertNull(readPart.typeMetadata, "ReadPart output is intentionally unknown without a source declaration")
    }


    @Test
    fun declaredDataSchemaPublishesColumnsWithoutResolvingOrInspectingFiles() {
        val validation = validate("test/job/run/job-read-schema-validation-test.yaml")
        val read = validation.workerValidations[ObjectPath.parse("main.workers/read")]
        assertNotNull(read)
        assertNull(read.errorMessage)
        assertNull(read.typeMetadata)
        val record = assertNotNull(read.contract?.structural as? DataType.Record)
        assertEquals(listOf("city", "amount"), record.fields.map { it.id.name })
    }


    @Test
    fun validationDigestTracksSourceAndStrongSchemaClosure() {
        val job = DocumentPath.parse("test/job/run/job-read-schema-validation-test.yaml")
        val source = ObjectLocation.parse(
            "test/job/run/job-read-schema-validation-test.yaml#main.sources/input")
        val schema = ObjectLocation.parse("test/job/run/job-read-schema-source-test.yaml#main")
        val baseNotation = AutoTestUtils.readNotation()
        val base = AutoTestUtils.graphDefinitionAttempt(baseNotation).transitiveSuccessful
        val baseKey = assertNotNull(JobValidationDigest.documentClosureKey(job, base))

        val sourceEdit = NotationReducer().applyStructural(
            baseNotation,
            UpsertAttributeCommand(
                source, AttributeName("groupPattern"), ScalarAttributeNotation("(?<group>.*)")))
            .graphNotation
        val sourceKey = assertNotNull(JobValidationDigest.documentClosureKey(
            job, AutoTestUtils.graphDefinitionAttempt(sourceEdit).transitiveSuccessful))

        val schemaEdit = NotationReducer().applyStructural(
            baseNotation,
            UpdateInAttributeCommand(
                schema,
                AttributePath.parse("fields.amount.class"),
                ScalarAttributeNotation("kotlin.Long")))
            .graphNotation
        val schemaKey = assertNotNull(JobValidationDigest.documentClosureKey(
            job, AutoTestUtils.graphDefinitionAttempt(schemaEdit).transitiveSuccessful))

        assertNotEquals(baseKey, sourceKey)
        assertNotEquals(baseKey, schemaKey)
    }


    @Test
    fun validationDigestUsesInheritedCrossDocumentProviderSourceAndSchema() {
        val job = DocumentPath.parse("test/job/run/job-read-schema-inherited-job-test.yaml")
        val source = ObjectLocation.parse(
            "test/job/run/job-read-schema-validation-test.yaml#main.sources/input")
        val schema = ObjectLocation.parse("test/job/run/job-read-schema-source-test.yaml#main")
        val baseNotation = AutoTestUtils.readNotation()
        val baseKey = assertNotNull(JobValidationDigest.documentClosureKey(
            job, AutoTestUtils.graphDefinitionAttempt(baseNotation).transitiveSuccessful))

        val sourceEdit = NotationReducer().applyStructural(
            baseNotation,
            UpsertAttributeCommand(
                source, AttributeName("groupPattern"), ScalarAttributeNotation("(?<group>.*)")))
            .graphNotation
        val sourceKey = assertNotNull(JobValidationDigest.documentClosureKey(
            job, AutoTestUtils.graphDefinitionAttempt(sourceEdit).transitiveSuccessful))

        val schemaEdit = NotationReducer().applyStructural(
            baseNotation,
            UpdateInAttributeCommand(
                schema,
                AttributePath.parse("fields.amount.class"),
                ScalarAttributeNotation("kotlin.Long")))
            .graphNotation
        val schemaKey = assertNotNull(JobValidationDigest.documentClosureKey(
            job, AutoTestUtils.graphDefinitionAttempt(schemaEdit).transitiveSuccessful))

        assertNotEquals(baseKey, sourceKey)
        assertNotEquals(baseKey, schemaKey)
    }


    @Test
    fun readPartRejectsKnownNonDataUnitInput() {
        val validation = validate("test/job/run/job-read-part-wrong-input-test.yaml")
        val readPart = validation.workerValidations[ObjectPath.parse("main.workers/readPart")]
        assertNotNull(readPart)
        assertContains(readPart.errorMessage ?: "", "DataUnit")
        assertContains(readPart.errorMessage ?: "", "Int")
        assertNull(readPart.typeMetadata)
    }


    @Test
    fun brokenExpressionIsValidationErrorNotCrash() {
        val validation = validate("test/job/job-validator-error-test.yaml")

        val source = validation.workerValidations[ObjectPath.parse("main.workers/source")]
        assertNotNull(source, "the broken Worker gets an entry")
        assertNotNull(source.errorMessage, "the compile failure is the Worker's validation error")
        assertNull(source.typeMetadata, "a broken expression types nothing")

        val collect = validation.workerValidations[ObjectPath.parse("main.workers/collect")]
        assertNotNull(collect)
        assertNull(collect.errorMessage, "only the broken Worker carries the error")
    }


    @Test
    fun runWorkerTypedByScriptCalleeDeclaredResult() {
        val validation = validate("test/job/run/job-run-host-test.yaml")

        // The child Script (script-engine-child-test) declares `results: main: kotlin.Int`.
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/run"))
        // The downstream sink forwards it (identity).
        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/sink"))
    }


    @Test
    fun undeclaredResultComponentIsSinkValidationError() {
        // Strict Script parity: a ResultSink whose component (blank -> main) is not declared in the Job's
        // `results` signature map errors on ITS card; the well-formed source stays clean.
        val validation = validate("test/job/signature/job-result-undeclared-test.yaml")

        val collect = validation.workerValidations[ObjectPath.parse("main.workers/collect")]
        assertNotNull(collect)
        assertEquals("No result type declared in the Job signature for 'main'", collect.errorMessage)

        val source = validation.workerValidations[ObjectPath.parse("main.workers/source")]
        assertNotNull(source)
        assertNull(source.errorMessage, "only the sink carries the error")
    }


    @Test
    fun writerYieldersValidateDeclaredComponentsAndCollisionsDeterministically() {
        val validation = validate("test/job/signature/job-result-yielder-collision-test.yaml")
        val collision = "Multiple result yielders for 'main': first, second"
        val mismatch = "Writer result 'main' declares String but the writer yields DataRef"
        assertEquals("$mismatch; $collision", validation.workerValidations.getValue(
            ObjectPath.parse("main.workers/first")).errorMessage)
        assertEquals("$mismatch; $collision", validation.workerValidations.getValue(
            ObjectPath.parse("main.workers/second")).errorMessage)
        assertEquals(
            "No result type declared in the Job signature for 'missing'",
            validation.workerValidations.getValue(
                ObjectPath.parse("main.workers/undeclared")).errorMessage)

        val validWriter = validate("test/job/run/job-per-unit-child-test.yaml")
            .workerValidations.getValue(ObjectPath.parse("main.workers/write"))
        assertNull(validWriter.errorMessage)
    }


    @Test
    fun declaredResultTypeMismatchIsSinkValidationError() {
        // The static assignability check: the source streams Int but the document declares `main` as String —
        // the sink's probe compile fails and the mismatch errors on ITS card before running.
        val validation = validate("test/job/signature/job-result-type-mismatch-test.yaml")

        val collect = validation.workerValidations[ObjectPath.parse("main.workers/collect")]
        assertNotNull(collect)
        assertEquals("Result 'main' declares String but the stream carries Int", collect.errorMessage)

        val source = validation.workerValidations[ObjectPath.parse("main.workers/source")]
        assertNotNull(source)
        assertNull(source.errorMessage, "only the sink carries the error")
    }


    @Test
    fun declaredSupertypeResultValidatesClean() {
        // TRUE assignability, not class-name equality: an Int stream into a declared Number result is fine
        // (Kotlin's own subtyping via the probe compile).
        val validation = validate("test/job/signature/job-result-subtype-test.yaml")

        assertEquals(TypeMetadata.int, typeOf(validation, "main.workers/collect"))
        assertEquals(
            listOf(), validation.workerValidations.values.mapNotNull { it.errorMessage },
            "an Int stream into a declared Number result validates clean")
    }


    @Test
    fun nullableLaneIntoNonNullableResultIsSinkValidationError() {
        // Nullability is part of assignability: a String? lane (nullable parameter source) into a declared
        // NON-nullable String result is rejected statically, consistent with the empty-stream run contract.
        val validation = validate("test/job/signature/job-result-nullability-test.yaml")

        val collect = validation.workerValidations[ObjectPath.parse("main.workers/collect")]
        assertNotNull(collect)
        assertEquals("Result 'main' declares String but the stream carries String?", collect.errorMessage)
    }


    @Test
    fun manuallyWiredCsvLaneSkipsWithoutFalseErrors() {
        // job-filter-expression-test wires its channels MANUALLY (non-blank ports), so the order-driven
        // derivation contributes no connections: every lane is unknown, so the Filter's `where` over runtime
        // CSV columns is only parsed — `amount.number > 2` references columns that cannot resolve here, and
        // must NOT be reported. No payload type shows either.
        val validation = validate("test/job/report/job-filter-expression-test.yaml")

        for ((path, entry) in validation.workerValidations) {
            assertNull(entry.errorMessage, "no false static error on $path")
            assertNull(entry.typeMetadata, "no payload type on the flat lane $path")
        }
    }


    @Test
    fun malformedExpressionOnCsvLaneIsStillCaught() {
        // The counterpart: a CSV lane's columns are unknown, but malformed source could not compile under any
        // header, so it must surface on the card rather than crashing the run on the first record.
        val validation = validate("test/job/job-syntax-unknown-columns-test.yaml")

        val formula = validation.workerValidations[ObjectPath.parse("main.workers/formula")]
        assertNotNull(formula, "the broken Worker gets an entry")
        assertNotNull(formula.errorMessage, "malformed source is caught without knowing the columns")

        val reader = validation.workerValidations[ObjectPath.parse("main.workers/reader")]
        assertNotNull(reader)
        assertNull(reader.errorMessage, "only the broken Worker carries the error")
    }


    @Test
    fun readWorkerNominalSourceFailuresStayVisibleInValidation() {
        val cases = listOf(
            "test/job/run/job-read-blank-source-test.yaml" to "No data source selected",
            "test/job/run/job-read-dangling-source-test.yaml" to "Unable to resolve",
            "test/job/run/job-read-wrong-source-test.yaml" to "not a DataSource")

        for ((document, expectedError) in cases) {
            val validation = validate(document)
            val read = validation.workerValidations[ObjectPath.parse("main.workers/read")]
            assertNotNull(read, "the ReadWorker definition must survive for $document")
            assertContains(read.errorMessage ?: "", expectedError)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun typeOf(validation: JobValidation, path: String): TypeMetadata? {
        val entry = validation.workerValidations[ObjectPath.parse(path)]
        assertNotNull(entry, "expected a validation entry for $path")
        return entry.typeMetadata
    }


    private fun validate(path: String): JobValidation {
        context = KzenAutoContext.forTest()
        val documentPath = DocumentPath.parse(path)

        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful

        val synthesis = JobChannelSynthesis(context.notationMetadataReader)
            .synthesize(graphDefinition, documentPath)
        val filteredDefinition = synthesis.graphDefinition.filterTransitive(documentPath)
        val graphInstance = GraphCreator.createGraph(filteredDefinition, context.graphEnvironment)

        return JobValidator.validate(
            documentPath, graphDefinition, graphInstance, context.graphEnvironment)
    }
}
