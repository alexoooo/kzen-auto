package tech.kzen.auto.server.api

import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer


// Fix screenshot upload, see:
//   https://stackoverflow.com/a/60948184/1941359
@Configuration
class WebfluxConfig : WebFluxConfigurer {
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer.defaultCodecs().maxInMemorySize(128 * 1024 * 1024)
    }
}