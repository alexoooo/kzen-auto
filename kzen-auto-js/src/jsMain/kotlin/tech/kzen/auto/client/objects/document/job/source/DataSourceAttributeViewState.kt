package tech.kzen.auto.client.objects.document.job.source

import react.State
import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


external interface DataSourceAttributeViewState: State {
    var openDocumentPath: DocumentPath?
    var sourceLocation: ObjectLocation?
    var sourceType: String?
    var missingReference: String?
    var validation: StepValidation?
    var authoring: Boolean
    var authoringError: String?
}
