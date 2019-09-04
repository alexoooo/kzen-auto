package tech.kzen.auto.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux
import java.awt.Robot


@EnableWebFlux
@SpringBootApplication
class KzenAutoApp


fun main(args: Array<String>) {
    // NB: activate non-headless mode
    Robot()

    runApplication<KzenAutoApp>(*args)
}
