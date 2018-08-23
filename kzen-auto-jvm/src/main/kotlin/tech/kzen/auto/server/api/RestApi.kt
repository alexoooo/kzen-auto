package tech.kzen.auto.server.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.router


@Configuration
class RestApi(
        private val counterHandler: RestHandler
) {
    @Bean
    fun counterRouter() = router {
        GET("/scan", counterHandler::scan)
        GET("/notation/**", counterHandler::notation)


        GET("/command/create", counterHandler::commandCreatePackge)
        GET("/command/delete", counterHandler::commandDeletePackge)

        GET("/command/add", counterHandler::commandAddObject)
        GET("/command/remove", counterHandler::commandRemoveObject)
        GET("/command/shift", counterHandler::commandShiftObject)
        GET("/command/rename", counterHandler::commandRenameObject)

        GET("/command/edit", counterHandler::commandEditParameter)


        GET("/action/perform", counterHandler::actionPerform)

        // provide value from client
//        GET("/action/submit", counterHandler::actionSubmit)


        GET("/", counterHandler::resource)
        GET("/**", counterHandler::resource)
    }
}