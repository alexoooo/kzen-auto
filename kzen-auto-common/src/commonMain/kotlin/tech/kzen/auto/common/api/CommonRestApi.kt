package tech.kzen.auto.common.api


object CommonRestApi {
    // read document object model notation
    const val scan = "/scan"
    const val notationPrefix = "/notation/"
    const val resource = "/resource"

    // modify document object model
    private const val commandPrefix = "/command/"

    private const val commandDocumentPrefix = "${commandPrefix}document/"
    const val commandDocumentCreate = "${commandDocumentPrefix}create"
    const val commandDocumentDelete = "${commandDocumentPrefix}delete"

    private const val commandObjectPrefix = "${commandPrefix}object/"
    const val commandObjectAdd = "${commandObjectPrefix}add"
    const val commandObjectRemove = "${commandObjectPrefix}remove"
    const val commandObjectShift = "${commandObjectPrefix}shift"
    const val commandObjectRename = "${commandObjectPrefix}rename"
    const val commandObjectInsertInList = "${commandObjectPrefix}insert-in-list"
    const val commandObjectRemoveIn = "${commandObjectPrefix}remove-in"

    private const val commandAttributePrefix = "${commandPrefix}attribute/"
    const val commandAttributeUpsert = "${commandAttributePrefix}upsert"
//    const val commandAttributeClear = "${commandAttributePrefix}clear"
    const val commandAttributeUpdateIn = "${commandAttributePrefix}update-in"
    const val commandAttributeUpdateAllNestingsIn = "${commandAttributePrefix}update-nestings-in"
    const val commandAttributeUpdateAllValuesIn = "${commandAttributePrefix}update-values-in"
    const val commandAttributeInsertItemIn = "${commandAttributePrefix}insert-item-in"
    const val commandAttributeInsertAllItemsIn = "${commandAttributePrefix}insert-items-in"
    const val commandAttributeInsertEntryIn = "${commandAttributePrefix}insert-entry-in"
    const val commandAttributeRemoveIn = "${commandAttributePrefix}remove-in"
    const val commandAttributeRemoveItemIn = "${commandAttributePrefix}remove-item-in"
    const val commandAttributeRemoveAllItemsIn = "${commandAttributePrefix}remove-items-in"
    const val commandAttributeShiftIn = "${commandAttributePrefix}shift-in"

    private const val commandRefactorPrefix = "${commandPrefix}refactor/"
    const val commandRefactorObjectRename = "${commandRefactorPrefix}rename"
    const val commandRefactorDocumentRename = "${commandRefactorPrefix}rename-doc"

    private const val commandResourcePrefix = "${commandPrefix}resource/"
    const val commandResourceAdd = "${commandResourcePrefix}add"
    const val commandResourceRemove = "${commandResourcePrefix}remove"

    const val commandBenchmark = "${commandPrefix}benchmark"


    const val paramHostDocumentPath = "host"
    const val paramDocumentPath = "path"
    const val paramObjectPath = "object"
    const val paramPositionIndex = "index"
    const val paramSecondaryPosition = "position"
    const val paramObjectNotation = "body"
    const val paramObjectName = "name"
    const val paramDocumentName = "file"
    const val paramDocumentNotation = "document"
    const val paramAttributeName = "attribute"
    const val paramAttributePath = "in-attribute"
    const val paramAttributeNesting = "nest"
    const val paramAttributeKey = "key"
    const val paramAttributeNotation = "value"
    const val paramResourcePath = "resource"
    const val paramFresh = "fresh"
    const val paramAttributeCreateContainer = "create-ancestors"
    const val paramAttributeCleanupContainer = "cleanup-container"
    const val paramTaskId = "task"
    const val paramRunId = "run"
    const val paramExecutionId = "execution"
    const val paramAction = "action"


    private const val actionPrefix = "/action/"

    // synchronous request/response
    const val actionDetached = "${actionPrefix}detached"
    const val actionDetachedDownload = "${actionPrefix}download"

    // asynchronous background job
    private const val taskPrefix = "/task/"
    const val taskSubmit = "${taskPrefix}submit"
    const val taskCancel = "${taskPrefix}cancel"
    const val taskLookup = "${taskPrefix}lookup"
    const val taskQuery = "${taskPrefix}query"

    // managed execution graph
    private const val logicPrefix = "/logic/"
    const val logicStatus = "${logicPrefix}status"
    const val logicStart = "${logicPrefix}start"
    const val logicRequest = "${logicPrefix}request"
    const val logicCancel = "${logicPrefix}cancel"
    const val logicPause = "${logicPrefix}pause"
    const val logicContinueRun = "${logicPrefix}run"

    // script (ad hoc)
    const val actionList = "${actionPrefix}list"
    const val actionModel = "${actionPrefix}model"
    const val actionStart = "${actionPrefix}start"
    const val actionReturn = "${actionPrefix}return"
    const val actionReset = "${actionPrefix}reset"
    const val actionPerform = "${actionPrefix}perform"
    const val fieldDigest = "digest"

    // dataflow (ad hoc)
    private const val execPrefix = "/exec/"
    const val execModel = "${execPrefix}model"
    const val execReset = "${execPrefix}reset"
    const val execPerform = "${execPrefix}perform"
}