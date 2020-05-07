package tech.kzen.auto.common.objects.document.filter


data class CriteriaSpec(
    val columnRequiredValues: Map<String, Set<String>>
)