package com.frerox.toolz.widget

import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFrameValidation
import com.frerox.toolz.widget.livingframes.LivingFramesSlideshow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun plan_alwaysFitsTheProvidedBudget() {
        for (n in listOf(1, 2, 4, 12, 40)) {
            for (opaque in listOf(true, false)) {
                for (mode in LivingFrameLoopMode.entries) {
                    for (budget in listOf(1_000L, 100_000L, 1_000_000L, 8_500_000L)) {
                        val p = LivingFrameEngine.plan(n, 1080, 2400, opaque, 30, budget, mode)
                        assertTrue(
                            "n=$n opaque=$opaque mode=$mode budget=$budget bytes=${p.bytes}",
                            p.bytes <= budget
                        )
                        assertTrue(p.outW >= 2 && p.outH >= 2)
                        assertTrue(p.frames in 1..n)
                        assertTrue(p.step in 1..n)
                    }
                }
            }
        }
    }

    @Test
    fun plan_rescalesFpsWhenFramesAreSampled() {
        val p = LivingFrameEngine.plan(40, 1080, 2400, true, 30, 1_000_000L)
        if (p.step > 1) {
            assertEquals(LivingFrameFps.snap(30f / p.step), p.effectiveFps)
        }
    }

    @Test
    fun validation_rejectsUnsafeIds() {
        val base = LivingFrame(
            id = "../escape", name = "x",
            type = LivingFrameType.PHOTO,
            frameCount = 1, width = 100, height = 100
        )
        assertNull(LivingFrameValidation.normalize(base))
        assertNull(LivingFrameValidation.normalize(base.copy(id = "")))
    }

    @Test
    fun validation_clampsCorruptMetadata() {
        val frame = LivingFrame(
            id = "safe_id-1",
            name = "  ",
            type = LivingFrameType.SLIDESHOW,
            frameCount = 999,
            width = 99_999,
            height = -5,
            fps = 999,
            opacity = 5f,
            cornerRadiusDp = 999,
            slideshowIntervalMin = 99_999
        )
        val fixed = LivingFrameValidation.normalize(frame)!!
        assertEquals("Untitled", fixed.name)
        assertEquals(30, fixed.frameCount)
        assertEquals(8_192, fixed.width)
        assertEquals(1, fixed.height)
        assertEquals(30, fixed.fps)
        assertEquals(1f, fixed.opacity)
        assertEquals(48, fixed.cornerRadiusDp)
        assertEquals(1_440, fixed.slideshowIntervalMin)
    }

    @Test
    fun plan_extremeAspectStillFitsATwoPixelBudget() {
        for ((opaque, budget) in listOf(true to 72L, false to 80L)) {
            val p = LivingFrameEngine.plan(40, 2, 8_000, opaque, 30, budget)
            assertTrue("opaque=$opaque bytes=${p.bytes}", p.bytes <= budget)
            assertEquals(2, p.outW)
            assertEquals(2, p.outH)
            assertEquals(1, p.frames)
        }
    }

    @Test
    fun validation_clampsFrameCountByType() {
        val base = LivingFrame(
            id = "typed", name = "x", type = LivingFrameType.GIF,
            frameCount = 999, width = 100, height = 100
        )
        assertEquals(40, LivingFrameValidation.normalize(base)!!.frameCount)
        assertEquals(
            30,
            LivingFrameValidation.normalize(base.copy(type = LivingFrameType.SLIDESHOW))!!.frameCount
        )
        assertEquals(
            1,
            LivingFrameValidation.normalize(base.copy(type = LivingFrameType.PHOTO))!!.frameCount
        )
    }

    @Test
    fun shuffle_cyclesNeverRepeatAtTheSeam() {
        val count = 7
        var previousLast: Int? = null
        for (position in 0 until count * 20) {
            val index = LivingFrameEngine.slideIndex(position, count, true, 1234L)
            if (position % count == 0 && previousLast != null) {
                assertTrue("cycle seam repeated $index", index != previousLast)
            }
            if (position % count == count - 1) previousLast = index
        }
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
