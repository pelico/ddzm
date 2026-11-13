package com.ddzm.media

import com.ddzm.core.contract.MediaSnapshot
import com.ddzm.core.contract.MediaTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStateRepositoryTest {

    private class FakeTransport : MediaTransport {
        val calls = mutableListOf<String>()
        override fun play(): Boolean = calls.add("play")
        override fun pause(): Boolean = calls.add("pause")
        override fun next(): Boolean = calls.add("next")
        override fun previous(): Boolean = calls.add("previous")
    }

    @Test
    fun subscribeImmediatelyReceivesCurrentSnapshot() {
        val repo = MediaStateRepository()
        val track = MediaSnapshot(title = "Song", artist = "Band", sessionActive = true)
        repo.publish(track)

        var received: MediaSnapshot? = null
        repo.subscribe { received = it }

        assertEquals(track, received)
        assertTrue(received!!.hasTrack)
    }

    @Test
    fun unsubscribeStopsDelivery() {
        val repo = MediaStateRepository()
        var count = 0
        val unsubscribe = repo.subscribe { count++ }
        assertEquals(1, count)

        repo.publish(MediaSnapshot(title = "Next"))
        assertEquals(2, count)

        unsubscribe()
        repo.publish(MediaSnapshot(title = "Ignored"))
        assertEquals(2, count)
    }

    @Test
    fun clearResetsSnapshotAndTransport() {
        val repo = MediaStateRepository()
        val transport = FakeTransport()
        repo.attachTransport(transport)
        repo.publish(MediaSnapshot(title = "Playing", isPlaying = true, sessionActive = true))

        repo.clear()

        assertFalse(repo.current().sessionActive)
        assertFalse(repo.current().hasTrack)
        assertNull(repo.transport())
    }

    @Test
    fun refreshDelegatesToBoundServiceAndSwallowsFailure() {
        val repo = MediaStateRepository()
        var refreshed = 0
        repo.refreshAction = { refreshed++ }
        repo.refresh()
        assertEquals(1, refreshed)

        repo.refreshAction = { error("service gone") }
        repo.refresh() // must not throw
    }

    @Test
    fun transportCommandsAreForwarded() {
        val repo = MediaStateRepository()
        val transport = FakeTransport()
        repo.attachTransport(transport)

        val api = repo.transport()!!
        assertTrue(api.play())
        assertTrue(api.pause())
        assertTrue(api.next())
        assertTrue(api.previous())

        assertEquals(listOf("play", "pause", "next", "previous"), transport.calls)
    }
}
