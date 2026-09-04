package tech.kzen.auto.client.objects.document.common.file.format

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


abstract class FormatOverrideEditor(
    private val objectLocation: ObjectLocation
): ReactWrapper<FormatOverrideEditorProps> {
    fun name(): ObjectName = objectLocation.objectPath.name
}
