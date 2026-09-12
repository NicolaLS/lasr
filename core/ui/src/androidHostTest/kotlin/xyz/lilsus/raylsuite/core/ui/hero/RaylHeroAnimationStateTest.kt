package xyz.lilsus.raylsuite.core.ui.hero

import androidx.compose.runtime.BroadcastFrameClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RaylHeroAnimationStateTest {
    @Test
    fun successReturnsSmoothlyToContinuingIdlePulses() = runTest {
        val frameClock = BroadcastFrameClock()
        val state = RaylHeroAnimationState(RaylHeroGeometry.squares, RaylHeroGeometry.arcs)

        suspend fun advanceFrames(count: Int) {
            repeat(count) {
                advanceTimeBy(16)
                runCurrent()
                frameClock.sendFrame(currentTime * 1_000_000)
                runCurrent()
            }
        }

        val success = backgroundScope.launch(frameClock) {
            state.animatePhase(RaylHeroPhase.Succeeded)
        }
        runCurrent()
        advanceFrames(90)
        assertTrue(success.isCompleted && !success.isCancelled)
        RaylHeroGeometry.squares.indices.forEach { index ->
            assertEquals(0f, state.squareScale(index))
        }

        val idle = backgroundScope.launch(frameClock) {
            state.animatePhase(RaylHeroPhase.Ready)
        }
        runCurrent()
        advanceFrames(9)
        RaylHeroGeometry.squares.indices.forEach { index ->
            val scale = state.squareScale(index)
            assertTrue(scale > 0f && scale < 1f, "Square $index should restore gradually: $scale")
        }

        advanceFrames(30)
        repeat(2) {
            var pulsed = false
            var settled = false
            repeat(70) {
                advanceFrames(1)
                pulsed = pulsed || state.squareScale(0) > 1.05f
                settled = settled || state.squareScale(0) < 1.01f
            }
            assertTrue(pulsed && settled, "Idle pulses should keep growing and settling")
        }
        assertTrue(idle.isActive)
        idle.cancelAndJoin()
    }
}
