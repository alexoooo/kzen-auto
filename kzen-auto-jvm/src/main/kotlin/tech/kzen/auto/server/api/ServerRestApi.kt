package tech.kzen.auto.server.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.router
import tech.kzen.auto.common.api.CommonRestApi


@Configuration
class ServerRestApi(
        private val counterHandler: RestHandler
) {
    @Suppress("unused")
    @Bean
    fun counterRouter() = router {
        GET(CommonRestApi.scan, counterHandler::scan)
        GET("${CommonRestApi.notationPrefix}**", counterHandler::notation)
        GET(CommonRestApi.resource, counterHandler::resourceRead)

        GET(CommonRestApi.commandDocumentCreate, counterHandler::createDocument)
        GET(CommonRestApi.commandDocumentDelete, counterHandler::deleteDocument)

        GET(CommonRestApi.commandObjectAdd, counterHandler::addObject)
        GET(CommonRestApi.commandObjectRemove, counterHandler::removeObject)
        GET(CommonRestApi.commandObjectShift, counterHandler::shiftObject)
        GET(CommonRestApi.commandObjectRename, counterHandler::renameObject)
        GET(CommonRestApi.commandObjectInsertInList, counterHandler::insertObjectInList)
        GET(CommonRestApi.commandObjectRemoveIn, counterHandler::removeObjectInAttribute)

        GET(CommonRestApi.commandAttributeUpsert, counterHandler::upsertAttribute)
        GET(CommonRestApi.commandAttributeUpdateIn, counterHandler::updateInAttribute)
        GET(CommonRestApi.commandAttributeInsertItemIn, counterHandler::insertListItemInAttribute)
        GET(CommonRestApi.commandAttributeInsertAllItemsIn, counterHandler::insertAllListItemsInAttribute)
        GET(CommonRestApi.commandAttributeInsertEntryIn, counterHandler::insertMapEntryInAttribute)
        GET(CommonRestApi.commandAttributeRemoveIn, counterHandler::removeInAttribute)
        GET(CommonRestApi.commandAttributeRemoveItemIn, counterHandler::removeListItemInAttribute)
        GET(CommonRestApi.commandAttributeRemoveAllItemsIn, counterHandler::removeAllListItemsInAttribute)
        GET(CommonRestApi.commandAttributeShiftIn, counterHandler::shiftInAttribute)

        GET(CommonRestApi.commandRefactorObjectRename, counterHandler::refactorObjectName)
        GET(CommonRestApi.commandRefactorDocumentRename, counterHandler::refactorDocumentName)

        POST(CommonRestApi.commandResourceAdd, counterHandler::addResource)
        GET(CommonRestApi.commandResourceRemove, counterHandler::resourceDelete)

        GET(CommonRestApi.commandBenchmark, counterHandler::benchmark)

        GET(CommonRestApi.actionList, counterHandler::actionList)
        GET(CommonRestApi.actionModel, counterHandler::actionModel)
        GET(CommonRestApi.actionStart, counterHandler::actionStart)
        GET(CommonRestApi.actionReturn, counterHandler::actionReturn)
        GET(CommonRestApi.actionReset, counterHandler::actionReset)
        GET(CommonRestApi.actionPerform, counterHandler::actionPerform)

        GET(CommonRestApi.actionDetached, counterHandler::actionDetached)
        POST(CommonRestApi.actionDetached, counterHandler::actionDetached)
        GET(CommonRestApi.actionDetachedDownload, counterHandler::actionDetachedDownload)

        GET(CommonRestApi.execModel, counterHandler::execModel)
        GET(CommonRestApi.execReset, counterHandler::execReset)
        GET(CommonRestApi.execPerform, counterHandler::execPerform)

        GET(CommonRestApi.taskSubmit, counterHandler::taskSubmit)
        GET(CommonRestApi.taskQuery, counterHandler::taskQuery)
        GET(CommonRestApi.taskCancel, counterHandler::taskCancel)
//        GET(CommonRestApi.taskRequest, counterHandler::taskRequest)
//        POST(CommonRestApi.taskRequest, counterHandler::taskRequest)
        GET(CommonRestApi.taskLookup, counterHandler::taskLookup)

        // provide value from client
//        GET("/auto-jvm/submit", counterHandler::actionSubmit)

        GET("/", counterHandler::staticResource)
        GET("/**", counterHandler::staticResource)
    }
}