package tech.kzen.auto.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux


@EnableWebFlux
@SpringBootApplication
class KzenAutoApp


fun main(args: Array<String>) {
    // NB: disable headless mode
//    Robot()
    // https://stackoverflow.com/questions/40016683/spring-boot-forcing-headless-mode
    System.setProperty("java.awt.headless", "false")

    runApplication<KzenAutoApp>(*args)
}
