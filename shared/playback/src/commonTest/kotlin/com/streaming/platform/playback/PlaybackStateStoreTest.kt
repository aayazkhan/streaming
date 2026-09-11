package com.streaming.platform.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackStateStoreTest {
    @Test
    fun nativeEventsBecomePortableSharedState() {
        val store = PlaybackStateStore()
        store.onPlayerEvent(PlayerEvent(PlaybackState.BUFFERING, positionSeconds = 42, bufferedPositionSeconds = 48))

        assertEquals(PlaybackState.BUFFERING, store.state.value.state)
        assertEquals(42, store.state.value.positionSeconds)
        assertEquals(48, store.state.value.bufferedPositionSeconds)
    }
}
