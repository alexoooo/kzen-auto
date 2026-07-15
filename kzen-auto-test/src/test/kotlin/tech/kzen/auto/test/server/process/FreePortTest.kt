package tech.kzen.auto.test.server.process

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket


/**
 * Pins the semantics [KzenAutoProcess]'s pre-flight guard depends on. If [FreePort.isFree] ever answers
 * `true` for an occupied port, that guard fails open and the harness silently drives whatever process
 * already holds the port — the exact false-pass this mechanism exists to prevent.
 */
class FreePortTest {
    @Test
    fun nextReturnsABindablePort() {
        val port = FreePort.next()
        bindLoopback(port).use {
            // binding it back is the assertion; the close below releases it
        }
    }


    @Test
    fun occupiedPortIsNotFree() {
        bindLoopback(FreePort.next()).use { occupied ->
            assertFalse(FreePort.isFree(occupied.localPort), "a listening port must read as occupied")
        }
    }


    @Test
    fun releasedPortIsFreeAgain() {
        val socket = bindLoopback(FreePort.next())
        val port = socket.localPort
        assertFalse(FreePort.isFree(port))

        socket.close()
        assertTrue(FreePort.isFree(port), "a released port must read as free")
    }


    // Mirrors what a kzen-auto child binds (KzenAutoConfig.host = 127.0.0.1), which is the address
    //  FreePort probes — a wildcard bind here would not exercise the same thing.
    //
    //  SO_REUSEADDR is ON deliberately: this stands in for a real occupant (Netty sets it on its server
    //  socket), which is the harder case for the probe to detect — a probe that shared the flag could,
    //  on some platforms, bind straight through a live server and report the port free.
    private fun bindLoopback(port: Int): ServerSocket {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1)
        return socket
    }
}
