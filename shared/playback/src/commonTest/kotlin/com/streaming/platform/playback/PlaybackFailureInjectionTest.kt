package com.streaming.platform.playback

import kotlin.test.Test
import kotlin.test.assertTrue

class PlaybackFailureInjectionTest {
    @Test
    fun scriptedFaultsAreExplicitAndRecoverable() {
        val injector = ScriptedPlaybackFailureInjector(setOf(PlaybackFailurePoint.DRM_LICENSE))

        assertTrue(injector.check(PlaybackFailurePoint.DRM_LICENSE).isFailure)
        assertTrue(injector.check(PlaybackFailurePoint.CDN_SEGMENT).isSuccess)

        injector.disable(PlaybackFailurePoint.DRM_LICENSE)
        assertTrue(injector.check(PlaybackFailurePoint.DRM_LICENSE).isSuccess)
    }
}
