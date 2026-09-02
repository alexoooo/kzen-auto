package tech.kzen.auto.common.data.model

import tech.kzen.auto.common.data.read.ContentCodingSpec
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.data.read.ReaderCapabilityIdentity
import tech.kzen.auto.common.data.read.ResolvedReadSpec
import tech.kzen.lib.common.exec.MapExecutionValue


internal fun testDataPart(
    role: DataRole,
    ref: DataRef,
    readerName: String = "delimited"
): DataPart = DataPart(
    role,
    ref,
    DataContentFingerprint.localOrNull(ref),
    ResolvedReadSpec(
        ReaderCapabilityIdentity("test", readerName, "1"),
        listOf(ContentCodingSpec.identity),
        MapExecutionValue(emptyMap())))


internal fun testDataUnit(path: String): DataUnit =
    DataUnit.of(testDataPart(DataRole.main, DataRef(null, path)))
