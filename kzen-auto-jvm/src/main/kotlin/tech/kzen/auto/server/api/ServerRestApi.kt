package tech.kzen.auto.server.api
//
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.web.reactive.function.server.router
//import tech.kzen.auto.common.api.CommonRestApi
//
//
//@Configuration
//class ServerRestApi(
//        private val counterHandler: RestHandler
//) {
//    @Suppress("unused")
//    @Bean
//    fun counterRouter() = router {
//        GET(CommonRestApi.scan, counterHandler::scan)
//        GET("${CommonRestApi.notationPrefix}**", counterHandler::notation)
//        GET(CommonRestApi.resource, counterHandler::resourceRead)
//
//        GET(CommonRestApi.commandDocumentCreate, counterHandler::createDocument)
//        GET(CommonRestApi.commandDocumentDelete, counterHandler::deleteDocument)
//
//        GET(CommonRestApi.commandObjectAdd, counterHandler::addObject)
//        GET(CommonRestApi.commandObjectRemove, counterHandler::removeObject)
//        GET(CommonRestApi.commandObjectShift, counterHandler::shiftObject)
//        GET(CommonRestApi.commandObjectRename, counterHandler::renameObject)
//        GET(CommonRestApi.commandObjectInsertInList, counterHandler::insertObjectInList)
//        GET(CommonRestApi.commandObjectRemoveIn, counterHandler::removeObjectInAttribute)
//
//        GET(CommonRestApi.commandAttributeUpsert, counterHandler::upsertAttributeGet)
//        PUT(CommonRestApi.commandAttributeUpsert, counterHandler::upsertAttributePut)
//        GET(CommonRestApi.commandAttributeUpdateIn, counterHandler::updateInAttributeGet)
//        PUT(CommonRestApi.commandAttributeUpdateIn, counterHandler::updateInAttributePut)
//        GET(CommonRestApi.commandAttributeUpdateAllNestingsIn, counterHandler::updateAllNestingsInAttributeGet)
//        PUT(CommonRestApi.commandAttributeUpdateAllNestingsIn, counterHandler::updateAllNestingsInAttributePut)
//        GET(CommonRestApi.commandAttributeUpdateAllValuesIn, counterHandler::updateAllValuesInAttributeGet)
//        PUT(CommonRestApi.commandAttributeUpdateAllValuesIn, counterHandler::updateAllValuesInAttributePut)
//        GET(CommonRestApi.commandAttributeInsertItemIn, counterHandler::insertListItemInAttributeGet)
//        PUT(CommonRestApi.commandAttributeInsertItemIn, counterHandler::insertListItemInAttributePut)
//        GET(CommonRestApi.commandAttributeInsertAllItemsIn, counterHandler::insertAllListItemsInAttributeGet)
//        PUT(CommonRestApi.commandAttributeInsertAllItemsIn, counterHandler::insertAllListItemsInAttributePut)
//        GET(CommonRestApi.commandAttributeRemoveIn, counterHandler::removeInAttribute)
//        GET(CommonRestApi.commandAttributeInsertEntryIn, counterHandler::insertMapEntryInAttributeGet)
//        PUT(CommonRestApi.commandAttributeInsertEntryIn, counterHandler::insertMapEntryInAttributePut)
//        GET(CommonRestApi.commandAttributeRemoveItemIn, counterHandler::removeListItemInAttributeGet)
//        PUT(CommonRestApi.commandAttributeRemoveItemIn, counterHandler::removeListItemInAttributePut)
//        GET(CommonRestApi.commandAttributeRemoveAllItemsIn, counterHandler::removeAllListItemsInAttributeGet)
//        PUT(CommonRestApi.commandAttributeRemoveAllItemsIn, counterHandler::removeAllListItemsInAttributePut)
//        GET(CommonRestApi.commandAttributeShiftIn, counterHandler::shiftInAttribute)
//
//        GET(CommonRestApi.commandRefactorObjectRename, counterHandler::refactorObjectName)
//        GET(CommonRestApi.commandRefactorDocumentRename, counterHandler::refactorDocumentName)
//
//        POST(CommonRestApi.commandResourceAdd, counterHandler::addResource)
//        GET(CommonRestApi.commandResourceRemove, counterHandler::resourceDelete)
//
//        GET(CommonRestApi.commandBenchmark, counterHandler::benchmark)
//
//        // detached
//        GET(CommonRestApi.actionDetached, counterHandler::actionDetachedByQuery)
//        POST(CommonRestApi.actionDetached, counterHandler::actionDetachedByQuery)
//        PUT(CommonRestApi.actionDetached, counterHandler::actionDetachedByForm)
//        GET(CommonRestApi.actionDetachedDownload, counterHandler::actionDetachedDownload)
//
//        // task
//        GET(CommonRestApi.taskSubmit, counterHandler::taskSubmit)
//        GET(CommonRestApi.taskQuery, counterHandler::taskQuery)
//        GET(CommonRestApi.taskCancel, counterHandler::taskCancel)
//        GET(CommonRestApi.taskLookup, counterHandler::taskLookup)
//
//        // logic
//        GET(CommonRestApi.logicStatus, counterHandler::logicStatus)
//        GET(CommonRestApi.logicStart, counterHandler::logicStart)
//        GET(CommonRestApi.logicRequest, counterHandler::logicRequest)
//        GET(CommonRestApi.logicCancel, counterHandler::logicCancel)
//        GET(CommonRestApi.logicRun, counterHandler::logicRun)
//
//        // script
//        GET(CommonRestApi.actionList, counterHandler::actionList)
//        GET(CommonRestApi.actionModel, counterHandler::actionModel)
//        GET(CommonRestApi.actionStart, counterHandler::actionStart)
//        GET(CommonRestApi.actionReturn, counterHandler::actionReturn)
//        GET(CommonRestApi.actionReset, counterHandler::actionReset)
//        GET(CommonRestApi.actionPerform, counterHandler::actionPerform)
//
//        // dataflow
//        GET(CommonRestApi.execModel, counterHandler::execModel)
//        GET(CommonRestApi.execReset, counterHandler::execReset)
//        GET(CommonRestApi.execPerform, counterHandler::execPerform)
//
//
//        // provide value from client
////        GET("/auto-jvm/submit", counterHandler::actionSubmit)
//
//        GET("/", counterHandler::staticResource)
//        GET("/**", counterHandler::staticResource)
//    }
//}