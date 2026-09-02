package tech.kzen.auto.server.data.content.policy

import kotlin.time.Duration


class ContentTimeoutException(
    timeout: Duration
): RuntimeException("Content read exceeded timeout $timeout")
