package com.frerox.toolz.widget

import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.widget.livingframes.LivingFramesSlideshow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingFrameEngineTest {

    @Test
    fun fps_snapsToFlipperSteps() {
        assertEquals(12, LivingFrameFps.snap(12))
        assertEquals(30, LivingFrameFps.snap(30))
        assertEquals(30, LivingFrameFps.snap(100))
        assertEquals(1, LivingFrameFps.snap(-5))
        assertEquals(30, LivingFrameFps.MAX)
        assertEquals(12, LivingFrameFps.DEFAULT)
    }

    @Test
    fun fps_intervalMatchesFlipperLayouts() {
        assertEquals(1000, LivingFrameFps.intervalMs(1))
        assertEquals(83, LivingFrameFps.intervalMs(12))
        assertEquals(33, LivingFrameFps.intervalMs(30))
        assertEquals(11, LivingFrameFps.stepIndex(30))
    }

    @Test
    fun playbackOrder_loopIsForward() {
        assertEquals(listOf(0, 1, 2), LivingFrameEngine.playbackOrder(3, LivingFrameLoopMode.LOOP))
        assertEquals(listOf(0), LivingFrameEngine.playbackOrder(1, LivingFrameLoopMode.LOOP))
    }

    @Test
    fun playbackOrder_boomerangHasNoSeamDuplicate() {
        assertEquals(
            listOf(0, 1, 2, 3, 2, 1),
            LivingFrameEngine.playbackOrder(4, LivingFrameLoopMode.BOOMERANG)
        )
        assertEquals(
            listOf(0, 1),
            LivingFrameEngine.playbackOrder(2, LivingFrameLoopMode.BOOMERANG)
        )
    }

    @Test
    fun loopDuration_keepsRealTimeLength() {
        assertEquals(4 * 1000L / 12, LivingFrameEngine.loopDurationMs(4, 12, LivingFrameLoopMode.LOOP))
    }

    @Test
    fun plan_prefersFullFramesThenDegrades() {
        val full = LivingFrameEngine.plan(4, 100, 100, true, 12, 10_000_000L)
        assertEquals(1f, full.scale)
        assertEquals(1, full.step)
        assertEquals(false, full.reduced)
        val tight = LivingFrameEngine.plan(4, 1000, 1000, true, 12, 1000L)
        assertTrue(tight.reduced)
        assertTrue(tight.frames >= 1)
    }

    @Test
    fun shuffle_isDeterministicPermutation() {
        val a = LivingFrameEngine.shuffleOrder(5, 42L)
        val b = LivingFrameEngine.shuffleOrder(5, 42L)
        assertEquals(a, b)
        assertEquals(listOf(0, 1, 2, 3, 4), a.sorted())
    }

    @Test
    fun slideIndex_plainOrderWithoutShuffle() {
        assertEquals(2, LivingFrameEngine.slideIndex(7, 5, false, 1L))
        assertEquals(0, LivingFrameEngine.slideIndex(99, 1, true, 1L))
    }

    @Test
    fun slideshow_defaultsAreSane() {
        val frame = LivingFrame(
            id = "x", name = "s",
            type = LivingFrameType.SLIDESHOW,
            frameCount = 5, width = 100, height = 100
        )
        assertEquals(60, frame.slideshowIntervalMin)
        assertEquals(false, frame.shuffle)
        assertTrue(frame.isSlideshow)
    }

    @Test
    fun slideshow_intervalOptions_coverMinutesToDaily() {
        val opts = LivingFramesSlideshow.INTERVALS_MIN
        assertTrue(opts.contains(15))
        assertTrue(opts.contains(1440))
        assertTrue(opts.all { it in 1..1440 })
        assertEquals("1 hour", LivingFramesSlideshow.intervalLabel(60))
        assertEquals("Daily", LivingFramesSlideshow.intervalLabel(1440))
    }
}
