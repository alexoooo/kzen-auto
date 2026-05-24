package tech.kzen.auto.test.harness

import java.net.ServerSocket


object FreePort {
    fun next(): Int {
        return ServerSocket(0).use { it.localPort }
    }
}
