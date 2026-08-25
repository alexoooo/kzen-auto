package tech.kzen.auto.client.objects.document.common.file

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class FileBrowserToggleTest {
    private class RecordingObserver: FileBrowserToggleChannel.Observer {
        val notifications = mutableListOf<ObjectLocation>()
        override fun onFileBrowserToggled(objectLocation: ObjectLocation) {
            notifications.add(objectLocation)
        }
    }


    private val channel = FileBrowserToggleChannel()
    private val mainPath = DocumentPath.parse("main.yaml")
    private val first = ObjectLocation(mainPath, ObjectPath.parse("main.workers/File"))
    private val second = ObjectLocation(mainPath, ObjectPath.parse("main.workers/Other"))


    // Until a header claims a card, the editor keeps drawing its own toggle — this is the switch it reads.
    @Test
    fun aCardIsUnhostedUntilAHeaderClaimsIt() {
        assertFalse(channel.hosted(first))

        channel.host(first)

        assertTrue(channel.hosted(first))
        assertFalse(channel.hosted(second))
    }


    @Test
    fun hostingTwiceIsHarmless() {
        channel.host(first)
        channel.host(first)
        channel.unhost(first)

        assertFalse(channel.hosted(first))
    }


    @Test
    fun opennessIsPerCard() {
        channel.setOpen(first, true)

        assertTrue(channel.isOpen(first))
        assertFalse(channel.isOpen(second))
    }


    @Test
    fun observersOfOneCardHearOnlyItsChanges() {
        val observer = RecordingObserver()
        channel.observe(first, observer)

        channel.toggle(first)
        channel.toggle(second)

        assertEquals(listOf(first), observer.notifications)
    }


    // Both ends of a card write openness — the header on a click, the editor when adding the first file latches an
    // already-open browser. Re-asserting what is already true must not bounce a notification back.
    @Test
    fun anUnchangedWriteNotifiesNobody() {
        val observer = RecordingObserver()
        channel.observe(first, observer)

        channel.setOpen(first, true)
        channel.setOpen(first, true)
        channel.setOpen(first, false)
        channel.setOpen(first, false)

        assertEquals(listOf(first, first), observer.notifications)
    }


    @Test
    fun unobservedListenersStopHearing() {
        val observer = RecordingObserver()
        channel.observe(first, observer)
        channel.unobserve(first, observer)

        channel.toggle(first)

        assertEquals(listOf(), observer.notifications)
    }


    // A card leaving the document takes its openness with it: a Worker deleted while browsing, then re-added, must
    // not come back mid-browse.
    @Test
    fun unhostingForgetsOpenness() {
        channel.setOpen(first, true)

        channel.unhost(first)

        assertFalse(channel.isOpen(first))
    }
}
