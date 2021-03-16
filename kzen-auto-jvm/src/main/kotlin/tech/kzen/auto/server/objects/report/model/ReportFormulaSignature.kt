package tech.kzen.auto.server.objects.report.model

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec


data class ReportFormulaSignature(
    val columnNames: HeaderListing,
    val formula: FormulaSpec
)