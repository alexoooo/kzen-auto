package tech.kzen.auto.server.objects.job.worker.compatibility

import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedFormat


internal fun compatibilityDelimitedFormat(
    delimiter: String,
    header: Boolean
): ConfiguredDelimitedFormat = ConfiguredDelimitedFormat(
    "Existing delimited reader",
    emptyList(),
    false,
    delimiter,
    "\"",
    "double-quote",
    "lf",
    "none",
    if (header) "present" else "infer-labels",
    "UTF-8",
    "permit",
    "report",
    "report",
    "",
    null)
