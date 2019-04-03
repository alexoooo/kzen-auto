package tech.kzen.auto.server.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.router
import tech.kzen.auto.common.api.CommonRestApi


@Configuration
class ServerRestApi(
        private val counterHandler: RestHandler
) {
    @Bean
    fun counterRouter() = router {
        GET(CommonRestApi.scan, counterHandler::scan)
        GET("${CommonRestApi.notationPrefix}**", counterHandler::notation)


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
        GET(CommonRestApi.commandAttributeInsertEntryIn, counterHandler::insertMapEntryInAttribute)
        GET(CommonRestApi.commandAttributeRemoveIn, counterHandler::removeInAttribute)
        GET(CommonRestApi.commandAttributeShiftIn, counterHandler::shiftInAttribute)

        GET(CommonRestApi.commandRefactorObjectRename, counterHandler::refactorObjectName)
        GET(CommonRestApi.commandRefactorDocumentRename, counterHandler::refactorDocumentName)

        GET(CommonRestApi.actionModel, counterHandler::actionModel)
        GET(CommonRestApi.actionStart, counterHandler::actionStart)
        GET(CommonRestApi.actionReset, counterHandler::actionReset)
        GET(CommonRestApi.actionPerform, counterHandler::actionPerform)
        GET(CommonRestApi.actionDetached, counterHandler::actionDetached)

        // provide value from client
//        GET("/action/submit", counterHandler::actionSubmit)


        GET("/", counterHandler::resource)
        GET("/**", counterHandler::resource)
    }
}