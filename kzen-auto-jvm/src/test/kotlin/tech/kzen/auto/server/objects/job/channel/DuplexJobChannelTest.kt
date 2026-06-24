package tech.kzen.auto.server.objects.job.channel

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals


/**
 * Direct unit test of [DuplexJobChannel]'s request/reply mechanics — no graph, no notation. Verifies that a
 * client's request is matched to the server's reply, that concurrent in-flight requests each get their own
 * response, and that the server loop ends once the last client closes (close-on-last-client).
 */
class DuplexJobChannelTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun requestIsMatchedToServerReply() = runBlocking {
        val channel = DuplexJobChannel(0, external = false)
        val client = channel.newClient()

        // A serving "actor": reply to each request with request * 10, until the client closes.
        val serverJob = launch {
            for (served in channel.server) {
                served.reply((served.request as Int) * 10)
            }
        }

        assertEquals(30, client.request(3))
        assertEquals(50, client.request(5))

        client.close()
        serverJob.join()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun concurrentRequestsAreCorrelated() = runBlocking {
        // A buffer lets several requests be in flight before the server drains them; each must still come
        // back to the client that sent it (per-request CompletableDeferred correlation, no serialization).
        val channel = DuplexJobChannel(8, external = false)
        val client = channel.newClient()

        val serverJob = launch {
            for (served in channel.server) {
                served.reply((served.request as Int) + 1)
            }
        }

        val inFlight = (1..20).map { n ->
            async { client.request(n) }
        }
        val replies = inFlight.map { it.await() }

        assertEquals((1..20).map { it + 1 }, replies)

        client.close()
        serverJob.join()
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun serverLoopEndsOnlyAfterAllClientsClose() = runBlocking {
        val channel = DuplexJobChannel(0, external = false)
        val clientA = channel.newClient()
        val clientB = channel.newClient()

        val handled = mutableListOf<Int>()
        val serverJob = launch {
            for (served in channel.server) {
                handled.add(served.request as Int)
                served.reply(Unit)
            }
        }

        clientA.request(1)
        clientA.close()

        // One client still open: the server must keep serving.
        clientB.request(2)
        clientB.close()

        serverJob.join()
        assertEquals(listOf(1, 2), handled)
    }
}
