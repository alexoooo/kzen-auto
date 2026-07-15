package tech.kzen.auto.test.server.process

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket


/**
 * Port allocation and availability for spawned kzen-auto processes, so a harness run never contends
 * with a developer's own instances (nor with a concurrent harness run).
 *
 * Both calls probe the **loopback** address specifically, because that is what a kzen-auto child binds
 * (`KzenAutoConfig.host = "127.0.0.1"`, see KzenAutoMain.kzenAutoInit) — probing the wildcard address
 * instead would answer a different question than the one we care about.
 *
 * Both are inherently time-of-check-to-time-of-use: the probe socket must close before the child can
 * bind, so another process could take the port in between. The window is tiny and ephemeral ports are
 * handed out round-robin, so this is the standard approach — and [KzenAutoProcess] turns a lost race
 * into a loud, immediate failure rather than a silent one.
 */
object FreePort {
    /** An unbound port the OS just handed out. */
    fun next(): Int {
        return bindLoopback(0).use { it.localPort }
    }


    /** Whether [port] can be bound right now, i.e. nothing else is listening on it. */
    fun isFree(port: Int): Boolean {
        return try {
            bindLoopback(port).use { true }
        }
        catch (e: IOException) {
            false
        }
    }


    // SO_REUSEADDR pinned OFF rather than left to the default: Java explicitly does not define a
    //  ServerSocket's initial SO_REUSEADDR state, and where it is ON, a bind can succeed against a port
    //  another socket already holds — which would make isFree answer `true` for an occupied port, the
    //  guard failing open, the exact silent-success this mechanism exists to prevent. (Measured on
    //  Windows + JDK 26 the default already binds exclusively, so this is belt-and-braces there; it is
    //  cheap insurance for other platforms/JDKs.) The cost is that a port lingering in TIME_WAIT can
    //  read as occupied — a rare, LOUD false alarm, which is the direction to err in.
    private fun bindLoopback(port: Int): ServerSocket {
        val socket = ServerSocket()
        try {
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1)
        }
        catch (e: Throwable) {
            socket.close()
            throw e
        }
        return socket
    }
}
