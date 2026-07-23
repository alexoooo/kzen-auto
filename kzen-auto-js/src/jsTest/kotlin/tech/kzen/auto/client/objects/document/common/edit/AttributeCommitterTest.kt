package tech.kzen.auto.client.objects.document.common.edit

import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.service.store.RemoteGraphStore
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class AttributeCommitterTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainPath = DocumentPath.parse("main.yaml")
    private val objectLocation = ObjectLocation(mainPath, ObjectPath.parse("A"))
    private val attributePath = AttributePath.ofName(AttributeName("hello"))

    private val seedNotation = """
A:
  hello: "a"
"""


    //-----------------------------------------------------------------------------------------------------------------
    // Mirrors the real topology: the remote applies the same command to a twin store over identically seeded
    // media, so the digests match and the commit takes the clean success branch.
    private class TwinRemoteGraphStore(
        private val twin: DirectGraphStore
    ): RemoteGraphStore {
        override suspend fun apply(command: NotationCommand): Digest {
            twin.apply(command)
            return twin.digest()
        }
    }


    private class FailingRemoteGraphStore(
        private val cause: Throwable
    ): RemoteGraphStore {
        override suspend fun apply(command: NotationCommand): Digest {
            throw cause
        }
    }


    private suspend fun newDirectStore(): DirectGraphStore {
        val media = MapNotationMedia()
        media.writeDocument(mainPath, seedNotation)
        return DirectGraphStore(
            media, YamlNotationParser(), NotationMetadataReader(), GraphDefiner, NotationReducer())
    }


    private class Capture {
        var committed: AttributeNotation? = null
        var errorInvoked = false
        var errorMessage: String? = null
    }


    private fun committer(
        local: DirectGraphStore,
        remote: RemoteGraphStore,
        capture: Capture,
        pendingNotation: () -> AttributeNotation?
    ): AttributeCommitter {
        val mirroredGraphStore = MirroredGraphStore(local, remote)
        return AttributeCommitter(
            graphStore = { mirroredGraphStore },
            objectLocation = { objectLocation },
            attributePath = { attributePath },
            pendingNotation = pendingNotation,
            onCommitted = { capture.committed = it },
            onError = {
                capture.errorInvoked = true
                capture.errorMessage = it
            },
            editActivity = { null })
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun successCommitsThenReportsNoError() = async {
        val local = newDirectStore()
        val capture = Capture()
        val pending = ScalarAttributeNotation("b")

        committer(local, TwinRemoteGraphStore(newDirectStore()), capture) { pending }
            .commitNow()

        assertTrue(capture.errorInvoked)
        assertNull(capture.errorMessage)
        assertEquals(pending, capture.committed)
        assertEquals(
            "b",
            local.graphNotation().mergeAttribute(objectLocation, attributePath)?.asString())
    }


    @Test
    fun remoteFailureReportsMessageWithoutCommitting() = async {
        val local = newDirectStore()
        val capture = Capture()

        committer(local, FailingRemoteGraphStore(IllegalStateException("remote down")), capture) {
            ScalarAttributeNotation("b")
        }.commitNow()

        assertEquals("remote down", capture.errorMessage)
        assertNull(capture.committed)
    }


    @Test
    fun messagelessFailureFallsBackToToString() = async {
        val capture = Capture()

        committer(newDirectStore(), FailingRemoteGraphStore(Throwable()), capture) {
            ScalarAttributeNotation("b")
        }.commitNow()

        assertEquals(Throwable().toString(), capture.errorMessage)
        assertNull(capture.committed)
    }


    @Test
    fun nullPendingCommitsNothing() = async {
        val local = newDirectStore()
        val capture = Capture()

        committer(local, TwinRemoteGraphStore(newDirectStore()), capture) { null }
            .commitNow()

        assertFalse(capture.errorInvoked)
        assertNull(capture.committed)
        assertEquals(
            "a",
            local.graphNotation().mergeAttribute(objectLocation, attributePath)?.asString())
    }


    @Test
    fun explicitNotationCommitsDespiteNullPending() = async {
        val local = newDirectStore()
        val capture = Capture()
        val explicit = ScalarAttributeNotation("b")

        committer(local, TwinRemoteGraphStore(newDirectStore()), capture) { null }
            .commitNow(explicit)

        assertEquals(explicit, capture.committed)
        assertEquals(
            "b",
            local.graphNotation().mergeAttribute(objectLocation, attributePath)?.asString())
    }
}
