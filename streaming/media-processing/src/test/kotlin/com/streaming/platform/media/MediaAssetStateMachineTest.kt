package com.streaming.platform.media

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class MediaAssetStateMachineTest {
    @Test
    fun validLifecycleTransitionsAreEnforced() {
        val machine = MediaAssetStateMachine()
        val created = MediaAssetLifecycle("asset", "content", MediaAssetState.CREATED, updatedAt = Clock.System.now())
        val queued = machine.transition(
            machine.transition(
                machine.transition(created, MediaAssetState.UPLOADING),
                MediaAssetState.UPLOADED,
            ),
            MediaAssetState.VALIDATING,
        ).let { machine.transition(it, MediaAssetState.QUEUED) }
        assertEquals(MediaAssetState.QUEUED, queued.state)
    }

    @Test
    fun readyAssetCannotBeMutatedBackIntoProcessing() {
        val machine = MediaAssetStateMachine()
        val ready = MediaAssetLifecycle("asset", "content", MediaAssetState.READY, updatedAt = Clock.System.now())
        assertFailsWith<IllegalArgumentException> { machine.transition(ready, MediaAssetState.PROCESSING) }
    }
}
