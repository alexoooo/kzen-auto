package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface DataSourceAttributeViewProps: AttributeViewProps {
    var navigationGlobal: NavigationGlobal
    var mirroredGraphStore: MirroredGraphStore
}
