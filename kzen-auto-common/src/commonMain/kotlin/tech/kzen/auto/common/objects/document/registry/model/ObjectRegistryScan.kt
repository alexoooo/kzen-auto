package tech.kzen.auto.common.objects.document.registry.model

import tech.kzen.lib.platform.ClassName


data class ObjectRegistryScan(
    val classNames: Set<ClassName>
)