package tech.kzen.auto.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux


@EnableWebFlux
@SpringBootApplication
class KzenAutoApp


fun main(args: Array<String>) {
    runApplication<KzenAutoApp>(*args)
}
