package com.frerox.toolz.widget

import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameEstimate
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameQuality
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
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
        assertEquals(60, LivingFrameFps.snap(100))
        assertEquals(1, LivingFrameFps.snap(-5))
        assertEquals(60, LivingFrameFps.MAX)
        assertEquals(12, LivingFrameFps.DEFAULT)
        assertEquals(48, LivingFrameFps.snap(48))
        assertEquals(40, LivingFrameFps.snap(40))
    }

    @Test
    fun fps_atMostNeverExceedsTheCap() {
        // 900 frames over 20 s is 45 fps. Rounding up to 48 would need 960
        // frames and shorten the loop, so hard caps must round down.
        assertEquals(40, LivingFrameFps.atMost(45f))
        assertEquals(48, LivingFrameFps.atMost(48f))
        assertEquals(60, LivingFrameFps.atMost(100f))
        assertEquals(1, LivingFrameFps.atMost(0.5f))
    }

    @Test
    fun fps_intervalMatchesFlipperLayouts() {
        assertEquals(1000, LivingFrameFps.intervalMs(1))
        assertEquals(83, LivingFrameFps.intervalMs(12))
        assertEquals(33, LivingFrameFps.intervalMs(30))
        assertEquals(25, LivingFrameFps.intervalMs(40))
        assertEquals(21, LivingFrameFps.intervalMs(48))
        assertEquals(17, LivingFrameFps.intervalMs(60))
        assertEquals(11, LivingFrameFps.stepIndex(30))
        assertEquals(14, LivingFrameFps.stepIndex(60))
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
    fun loopDuration_appliesSpeed() {
        assertEquals(1_000L, LivingFrameEngine.loopDurationMs(12, 12, LivingFrameLoopMode.LOOP))
        assertEquals(500L, LivingFrameEngine.loopDurationMs(12, 12, LivingFrameLoopMode.LOOP, 2f))
        assertEquals(2_000L, LivingFrameEngine.loopDurationMs(12, 12, LivingFrameLoopMode.LOOP, 0.5f))
    }

    @Test
    fun displaySequence_identityAtSourceRate() {
        assertEquals(
            listOf(0, 1, 2, 3),
            LivingFrameEngine.displaySequence(4, 12, 12, 1f, LivingFrameLoopMode.LOOP)
        )
    }

    @Test
    fun displaySequence_speedResamplesWithoutReencoding() {
        // 2× drops every other source frame; 0.5× repeats each one.
        assertEquals(
            listOf(0, 2),
            LivingFrameEngine.displaySequence(4, 12, 12, 2f, LivingFrameLoopMode.LOOP)
        )
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 3, 3),
            LivingFrameEngine.displaySequence(4, 12, 12, 0.5f, LivingFrameLoopMode.LOOP)
        )
    }

    @Test
    fun displaySequence_higherDisplayFpsRepeatsFrames() {
        // 24 fps from a 12 fps capture repeats each frame. It cannot add detail,
        // but it keeps the real-time length and gives the launcher a smoother
        // flip cadence.
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 3, 3),
            LivingFrameEngine.displaySequence(4, 12, 24, 1f, LivingFrameLoopMode.LOOP)
        )
    }

    @Test
    fun displaySequence_includesBoomerangReturnPath() {
        assertEquals(
            listOf(0, 1, 2, 3, 2, 1),
            LivingFrameEngine.displaySequence(4, 12, 12, 1f, LivingFrameLoopMode.BOOMERANG)
        )
    }

    @Test
    fun requiredSourceSide_coversTheCroppedAxisForFill() {
        // A 466×473 widget showing a 16:9 clip keeps only the middle 466 px
        // of a 841×473 scaled source, so 473 alone would decode at 480×270
        // and upscale. The crop requires the source's long side.
        assertEquals(
            841,
            LivingFrameEngine.requiredSourceSide(
                466, 473, 1920, 1080, LivingFrameFit.FILL
            )
        )
        assertEquals(
            829,
            LivingFrameEngine.requiredSourceSide(
                466, 473, 1080, 1920, LivingFrameFit.FILL
            )
        )
        assertEquals(
            800,
            LivingFrameEngine.requiredSourceSide(
                800, 400, 1080, 1080, LivingFrameFit.FILL
            )
        )
    }

    @Test
    fun requiredSourceSide_fitOnlyNeedsTheWidgetBounds() {
        assertEquals(
            473,
            LivingFrameEngine.requiredSourceSide(
                466, 473, 1920, 1080, LivingFrameFit.FIT
            )
        )
        assertEquals(
            1600,
            LivingFrameEngine.requiredSourceSide(
                1600, 900, 1080, 1920, LivingFrameFit.FIT
            )
        )
    }

    @Test
    fun requiredSourceSide_isNeverZeroForTinyGeometry() {
        assertTrue(
            LivingFrameEngine.requiredSourceSide(
                1, 1, 1, 1, LivingFrameFit.FILL
            ) >= 1
        )
        assertEquals(
            2,
            LivingFrameEngine.requiredSourceSide(
                0, 0, 0, 0, LivingFrameFit.FIT
            )
        )
    }

    @Test
    fun displayDuration_keepsRealTimeLengthAcrossDisplayRates() {
        assertEquals(
            1_000L,
            LivingFrameEngine.displayDurationMs(12, 12, 12, 1f, LivingFrameLoopMode.LOOP)
        )
        assertEquals(
            1_000L,
            LivingFrameEngine.displayDurationMs(12, 12, 60, 1f, LivingFrameLoopMode.LOOP)
        )
        assertEquals(
            500L,
            LivingFrameEngine.displayDurationMs(12, 12, 60, 2f, LivingFrameLoopMode.LOOP)
        )
    }

    @Test
    fun estimate_preservesAspectAndCapsLongestEdge() {
        assertEquals(
            1920 to 1080,
            LivingFrameEstimate.storedSize(1920, 1080, LivingFrameQuality.HIGH)
        )
        assertEquals(
            1920 to 1080,
            LivingFrameEstimate.storedSize(3840, 2160, LivingFrameQuality.HIGH)
        )
        assertEquals(
            1080 to 1920,
            LivingFrameEstimate.storedSize(1080, 1920, LivingFrameQuality.HIGH)
        )
        // Never upscale a source that is already smaller than the preset.
        assertEquals(
            100 to 100,
            LivingFrameEstimate.storedSize(100, 100, LivingFrameQuality.COMPACT)
        )
        assertEquals(
            2 to 2,
            LivingFrameEstimate.storedSize(0, 0, LivingFrameQuality.COMPACT)
        )
    }

    @Test
    fun estimate_matchesMeasured1080pHighSet() {
        val bytes = LivingFrameEstimate.bytes(1920, 1080, LivingFrameQuality.HIGH, 900)
        // The real 900-frame High set the user measured was 62.5 MB. The old
        // estimate showed ~250 MB because it squared the preset side and
        // treated the WebP quality value as bytes.
        assertTrue("bytes=$bytes", bytes in 58_000_000L..68_000_000L)
        assertTrue(
            bytes < LivingFrameEstimate.bytes(1920, 1080, LivingFrameQuality.ULTRA, 900)
        )
        assertTrue(
            bytes > LivingFrameEstimate.bytes(1920, 1080, LivingFrameQuality.COMPACT, 900)
        )
    }

    @Test
    fun estimate_effectiveFpsHonoursPresetCap() {
        assertEquals(30, LivingFrameEstimate.effectiveFps(60, LivingFrameQuality.HIGH))
        assertEquals(15, LivingFrameEstimate.effectiveFps(60, LivingFrameQuality.COMPACT))
        assertEquals(12, LivingFrameEstimate.effectiveFps(12, LivingFrameQuality.COMPACT))
        assertEquals(1, LivingFrameEstimate.effectiveFps(0, LivingFrameQuality.ULTRA))
    }

    @Test
    fun sourceFps_fallsBackToFifteenForOldMetadata() {
        val base = LivingFrame(
            id = "old", name = "x", type = LivingFrameType.VIDEO,
            frameCount = 10, width = 100, height = 100, fps = 60
        )
        assertEquals(15, base.sourceFps)
        assertEquals(24, base.copy(capturedFps = 24).sourceFps)
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
        assertEquals(60, fixed.fps)
        assertEquals(1f, fixed.opacity)
        assertEquals(48, fixed.cornerRadiusDp)
        assertEquals(1_440, fixed.slideshowIntervalMin)
    }

    @Test
    fun validation_clampsSpeedResolutionAndCaptureRate() {
        val base = LivingFrame(
            id = "clamps", name = "x", type = LivingFrameType.VIDEO,
            frameCount = 10, width = 100, height = 100
        )
        assertEquals(4f, LivingFrameValidation.normalize(base.copy(speed = 99f))!!.speed)
        assertEquals(0.25f, LivingFrameValidation.normalize(base.copy(speed = -1f))!!.speed)
        assertEquals(1f, LivingFrameValidation.normalize(base.copy(speed = 1.1f))!!.speed)
        assertEquals(
            100,
            LivingFrameValidation.normalize(base.copy(resolutionPercent = 999))!!.resolutionPercent
        )
        assertEquals(
            40,
            LivingFrameValidation.normalize(base.copy(resolutionPercent = 0))!!.resolutionPercent
        )
        assertEquals(
            45,
            LivingFrameValidation.normalize(base.copy(resolutionPercent = 47))!!.resolutionPercent
        )
        assertEquals(
            60,
            LivingFrameValidation.normalize(base.copy(capturedFps = 999))!!.capturedFps
        )
        assertEquals(
            0,
            LivingFrameValidation.normalize(base.copy(capturedFps = -5))!!.capturedFps
        )
    }

    @Test
    fun validation_trimsTapTargetRoute() {
        val base = LivingFrame(
            id = "tap", name = "x", type = LivingFrameType.PHOTO,
            frameCount = 1, width = 100, height = 100,
            tapAction = LivingFrameTapAction.OPEN_TOOL,
            tapTargetRoute = "  notes/editor  "
        )
        assertEquals(
            "notes/editor",
            LivingFrameValidation.normalize(base)!!.tapTargetRoute
        )
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
        assertEquals(900, LivingFrameValidation.normalize(base)!!.frameCount)
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
