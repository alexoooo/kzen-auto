package tech.kzen.auto.common.api


object CommonRestApi {
    const val scan = "/scan"
    const val notationPrefix = "/notation/"


    private const val commandPrefix = "/command/"

    private const val commandDocumentPrefix = "${commandPrefix}document/"
    const val commandDocumentCreate = "${commandDocumentPrefix}create"
    const val commandDocumentDelete = "${commandDocumentPrefix}delete"

    private const val commandObjectPrefix = "${commandPrefix}object/"
    const val commandObjectAdd = "${commandObjectPrefix}add"
    const val commandObjectRemove = "${commandObjectPrefix}remove"
    const val commandObjectShift = "${commandObjectPrefix}shift"
    const val commandObjectRename = "${commandObjectPrefix}rename"
//    const val commandObjectRelocate = "${commandObjectPrefix}relocate"
    const val commandObjectInsertInList = "${commandObjectPrefix}insert-in-list"
    const val commandObjectRemoveIn = "${commandObjectPrefix}remove-in"

    private const val commandAttributePrefix = "${commandPrefix}attribute/"
    const val commandAttributeUpsert = "${commandAttributePrefix}upsert"
//    const val commandAttributeClear = "${commandAttributePrefix}clear"
    const val commandAttributeUpdateIn = "${commandAttributePrefix}update-in"
    const val commandAttributeInsertItemIn = "${commandAttributePrefix}insert-item-in"
    const val commandAttributeInsertEntryIn = "${commandAttributePrefix}insert-entry-in"
    const val commandAttributeRemoveIn = "${commandAttributePrefix}remove-in"
    const val commandAttributeShiftIn = "${commandAttributePrefix}shift-in"

    private const val commandRefactorPrefix = "${commandPrefix}refactor/"
    const val commandRefactorObjectRename = "${commandRefactorPrefix}rename"
    const val commandRefactorDocumentRename = "${commandRefactorPrefix}rename-doc"


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
    const val paramAttributeKey = "key"
    const val paramAttributeNotation = "value"


    private const val actionPrefix = "/action/"
    const val actionModel = "${actionPrefix}model"
    const val actionStart = "${actionPrefix}start"
    const val actionReset = "${actionPrefix}reset"
    const val actionPerform = "${actionPrefix}perform"
    const val actionDetached = "${actionPrefix}detached"


    private const val execPrefix = "/exec/"
    const val execModel = "${execPrefix}model"
    const val execReset = "${execPrefix}reset"
    const val execPerform = "${execPrefix}perform"
//    const val execDetached = "${execPrefix}detached"


    const val fieldDigest = "digest"
}