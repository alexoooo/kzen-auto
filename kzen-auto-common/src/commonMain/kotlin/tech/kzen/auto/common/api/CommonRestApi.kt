package tech.kzen.auto.common.api


object CommonRestApi {
    // read document object model notation
    const val scan = "/scan"
    const val notationPrefix = "/notation/"
    const val notationBatch = "/notation-batch"
    const val resource = "/resource"

    // modify document object model
    private const val commandPrefix = "/command/"

    private const val commandDocumentPrefix = "${commandPrefix}document/"
    const val commandDocumentCreate = "${commandDocumentPrefix}create"
    const val commandDocumentDelete = "${commandDocumentPrefix}delete"
    const val commandDocumentSetObjects = "${commandDocumentPrefix}set-objects"

    private const val commandObjectPrefix = "${commandPrefix}object/"
    const val commandObjectAdd = "${commandObjectPrefix}add"
    const val commandObjectRemove = "${commandObjectPrefix}remove"
    const val commandObjectShift = "${commandObjectPrefix}shift"
    const val commandObjectShiftTree = "${commandObjectPrefix}shift-tree"
    const val commandObjectRelocateTree = "${commandObjectPrefix}relocate-tree"
    const val commandObjectRename = "${commandObjectPrefix}rename"
    const val commandObjectAddAtAttribute = "${commandObjectPrefix}add-at-attribute"
    const val commandObjectInsertInList = "${commandObjectPrefix}insert-in-list"
    const val commandObjectRemoveIn = "${commandObjectPrefix}remove-in"

    private const val commandAttributePrefix = "${commandPrefix}attribute/"
    const val commandAttributeUpsert = "${commandAttributePrefix}upsert"
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
    // move a document or folder under a different parent folder (the path form distinguishes the two)
    const val commandRefactorMove = "${commandRefactorPrefix}move"

    private const val commandResourcePrefix = "${commandPrefix}resource/"
    const val commandResourceAdd = "${commandResourcePrefix}add"
    const val commandResourceRemove = "${commandResourcePrefix}remove"

    const val commandBenchmark = "${commandPrefix}benchmark"


    const val paramHostDocumentPath = "host"
    const val paramDocumentPath = "path"
    const val paramObjectPath = "object"
    const val paramObjectNesting = "object-nesting"
    const val paramPositionIndex = "index"
    const val paramSecondaryPosition = "position"
    const val paramObjectNotation = "body"
    const val paramObjectName = "name"
    const val paramDocumentName = "file"
    const val paramDocumentNesting = "nesting"
    const val paramDocumentNotation = "document"
    const val paramRawObjectsYaml = "raw-objects-yaml"
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
    const val paramPauseOnError = "pauseOnError"
    const val paramStepMode = "stepMode"


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
    const val logicStartAndRun = "${logicPrefix}startRun"
    const val logicRequest = "${logicPrefix}request"
    const val logicCancel = "${logicPrefix}cancel"
    const val logicPause = "${logicPrefix}pause"
    const val logicContinueRun = "${logicPrefix}run"
    const val logicContinueStep = "${logicPrefix}step"
    const val logicStepOver = "${logicPrefix}stepOver"
    const val logicStepOut = "${logicPrefix}stepOut"
    const val logicStartAndStep = "${logicPrefix}startStep"
    const val logicSetPauseOnError = "${logicPrefix}setPauseOnError"

    // stable object id mapping
    const val objectStableMapperSnapshot = "/object-stable/snapshot"

    // icon catalogue (Iconify on-demand protocol): GET /icon/{set}.json?icons=name1,name2,...
    // Served by the JVM backend from a bundled collection resource; the JS bundle holds no icon data.
    const val iconCollectionPrefix = "/icon/"
    const val paramIcons = "icons"
}