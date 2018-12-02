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


        GET(CommonRestApi.commandCreate, counterHandler::commandCreatePackge)
        GET(CommonRestApi.commandDelete, counterHandler::commandDeletePackge)

        GET(CommonRestApi.commandAdd, counterHandler::commandAddObject)
        GET(CommonRestApi.commandRemove, counterHandler::commandRemoveObject)
        GET(CommonRestApi.commandShift, counterHandler::commandShiftObject)
        GET(CommonRestApi.commandRename, counterHandler::commandRenameObject)

        GET(CommonRestApi.commandEdit, counterHandler::commandEditParameter)

        GET(CommonRestApi.actionModel, counterHandler::actionModel)
        GET(CommonRestApi.actionStart, counterHandler::actionStart)
        GET(CommonRestApi.actionReset, counterHandler::actionReset)
        GET(CommonRestApi.actionPerform, counterHandler::actionPerform)

        // provide value from client
//        GET("/action/submit", counterHandler::actionSubmit)


        GET("/", counterHandler::resource)
        GET("/**", counterHandler::resource)
    }
}