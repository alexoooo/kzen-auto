package tech.kzen.auto.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.config.EnableWebFlux


@EnableWebFlux
@SpringBootApplication
class KzenAutoMain


fun kzenAutoInit() {
    // NB: disable headless mode
    // https://stackoverflow.com/questions/40016683/spring-boot-forcing-headless-mode
    System.setProperty("java.awt.headless", "false")
}


fun main(args: Array<String>) {
//    KotlinCompiler().compileModule(
//        "testCompile",
//        listOf("C:/Users/ao/IdeaProjects/kzen-auto/build/compile/src/CompileTest.kt"),
//        File("C:/Users/ao/IdeaProjects/kzen-auto/build/compile/bin"),
//        listOf(),
//        object : ClassLoader() {}
//    )

    kzenAutoInit()
    runApplication<KzenAutoMain>(*args)
}
