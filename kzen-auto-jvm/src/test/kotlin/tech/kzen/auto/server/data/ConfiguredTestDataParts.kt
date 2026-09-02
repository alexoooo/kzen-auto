package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedTestFormats


fun configuredTestDataPart(
    role: DataRole,
    ref: DataRef,
    expectedFingerprint: DataContentFingerprint? = DataContentFingerprint.localOrNull(ref)
): DataPart = DataPart(
    role,
    ref,
    expectedFingerprint,
    ConfiguredDelimitedTestFormats.csv().resolvedRead(ref))
