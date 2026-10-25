package com.frerox.toolz.widget

import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameEstimate
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameLoopTransition
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
        // flipInterval is a whole number of milliseconds, so the flipper's
        // real rate is 12.05 or 58.8 fps and a loop can miss the nominal
        // length by one tick. What matters is that changing the display rate
        // does not make the same clip play noticeably longer or shorter.
        fun assertWithinOneTick(expected: Long, actual: Long, tickMs: Long) {
            assertTrue(
                "expected $expected ± $tickMs but was $actual",
                actual in (expected - tickMs)..(expected + tickMs)
            )
        }
        assertWithinOneTick(
            1_000L,
            LivingFrameEngine.displayDurationMs(12, 12, 12, 1f, LivingFrameLoopMode.LOOP),
            83L
        )
        assertWithinOneTick(
            1_000L,
            LivingFrameEngine.displayDurationMs(12, 12, 60, 1f, LivingFrameLoopMode.LOOP),
            17L
        )
        assertWithinOneTick(
            500L,
            LivingFrameEngine.displayDurationMs(12, 12, 60, 2f, LivingFrameLoopMode.LOOP),
            17L
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
    fun plan_staticUsesFullScaleWhenTheBudgetFits() {
        val full = LivingFrameEngine.planStatic(
            frameCount = 4,
            sourceFps = 12,
            speed = 1f,
            requestedFps = 12,
            widthPx = 100,
            heightPx = 100,
            opaque = true,
            budgetBytes = 10_000_000L,
            loopMode = LivingFrameLoopMode.LOOP
        )!!
        assertEquals(1f, full.scale)
        assertEquals(1, full.step)
        assertEquals(4, full.frames)
        assertEquals(12, full.effectiveFps)
        assertEquals(false, full.reduced)
        assertEquals(false, full.transition)
    }

    @Test
    fun plan_staticBudgetsUniqueBitmapsNotRepeatedDisplayChildren() {
        val p = LivingFrameEngine.planStatic(
            frameCount = 12,
            sourceFps = 12,
            speed = 1f,
            requestedFps = 60,
            widthPx = 100,
            heightPx = 100,
            opaque = true,
            budgetBytes = 1_000_000L,
            loopMode = LivingFrameLoopMode.LOOP
        )!!
        val steps = LivingFrameEngine.displaySteps(
            12, 12, 60, 1f, LivingFrameLoopMode.LOOP, LivingFrameLoopTransition.NONE
        )
        // 59 children, 12 pictures: charging all 59 would need ~2.4 MB.
        assertEquals(steps.size, p.frames)
        assertEquals(12, steps.toHashSet().size)
        assertEquals(60, p.effectiveFps)
        assertEquals(1f, p.scale)
        assertTrue(p.bytes <= 1_000_000L)
    }

    @Test
    fun plan_staticUsesRgb565BeforeDownscalingOpaqueVideo() {
        val p = LivingFrameEngine.planStatic(
            frameCount = 4,
            sourceFps = 12,
            speed = 1f,
            requestedFps = 12,
            widthPx = 1_000,
            heightPx = 500,
            opaque = true,
            budgetBytes = 5_000_000L,
            loopMode = LivingFrameLoopMode.LOOP
        )!!
        // ARGB at full size is ~8 MB; 565 at full size is ~4 MB, so keep the
        // pixels and spend colour depth instead of scaling the video down.
        assertEquals(1f, p.scale)
        assertTrue(p.rgb565)
        assertTrue(p.bytes <= 5_000_000L)
    }

    @Test
    fun plan_staticIncludesSeamBlendsInTheBudget() {
        val plain = LivingFrameEngine.planStatic(
            frameCount = 4,
            sourceFps = 12,
            speed = 1f,
            requestedFps = 12,
            widthPx = 100,
            heightPx = 100,
            opaque = true,
            budgetBytes = 10_000_000L,
            loopMode = LivingFrameLoopMode.LOOP,
            transition = LivingFrameLoopTransition.NONE
        )!!
        val faded = LivingFrameEngine.planStatic(
            frameCount = 4,
            sourceFps = 12,
            speed = 1f,
            requestedFps = 12,
            widthPx = 100,
            heightPx = 100,
            opaque = true,
            budgetBytes = 10_000_000L,
            loopMode = LivingFrameLoopMode.LOOP,
            transition = LivingFrameLoopTransition.FADE
        )!!
        assertEquals(4, plain.frames)
        assertEquals(4 + LivingFrameEngine.seamStepCount(12), faded.frames)
        assertEquals(true, faded.transition)
        assertTrue(faded.bytes > plain.bytes)
    }

    @Test
    fun plan_staticReturnsNullInsteadOfBlurringBelowItsFloor() {
        val p = LivingFrameEngine.planStatic(
            frameCount = 40,
            sourceFps = 30,
            speed = 1f,
            requestedFps = 30,
            widthPx = 1000,
            heightPx = 1000,
            opaque = true,
            budgetBytes = 1_000L,
            loopMode = LivingFrameLoopMode.LOOP
        )
        assertNull(p)
    }

    @Test
    fun plan_staticAlwaysFitsTheProvidedBudgetWhenItReturns() {
        for (n in listOf(2, 4, 12, 40)) {
            for (opaque in listOf(true, false)) {
                for (mode in LivingFrameLoopMode.entries) {
                    for (transition in LivingFrameLoopTransition.entries) {
                        for (budget in listOf(100_000L, 1_000_000L, 8_500_000L)) {
                            val p = LivingFrameEngine.planStatic(
                                n, 12, 1f, 30, 1080, 2400, opaque, budget,
                                mode, transition
                            ) ?: continue
                            val expected = LivingFrameEngine.displaySteps(
                                n, 12, p.effectiveFps, 1f, mode, transition
                            ).size
                            assertTrue(
                                "n=$n opaque=$opaque mode=$mode transition=$transition " +
                                        "budget=$budget bytes=${p.bytes}",
                                p.bytes <= budget
                            )
                            assertEquals(expected, p.frames)
                            assertTrue(p.outW >= 2 && p.outH >= 2)
                            assertTrue(p.scale >= LivingFrameEngine.MIN_STATIC_SCALE)
                            assertTrue(p.effectiveFps >= LivingFrameEngine.MIN_STATIC_FPS)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun plan_stillAlwaysFitsASaneBudget() {
        for (pixels in listOf(100 to 100, 1080 to 2400)) {
            for (opaque in listOf(true, false)) {
                for (budget in listOf(100_000L, 1_000_000L, 8_500_000L)) {
                    val p = LivingFrameEngine.planStill(
                        pixels.first, pixels.second, opaque, budget
                    )
                    assertTrue(
                        "opaque=$opaque budget=$budget bytes=${p.bytes}",
                        p.bytes <= budget
                    )
                    assertEquals(1, p.frames)
                    assertTrue(p.outW >= 2 && p.outH >= 2)
                }
            }
        }
    }

    @Test
    fun plan_extremeAspectStillFitsItsBudget() {
        for ((opaque, budget) in listOf(true to 4_096L, false to 4_096L)) {
            val p = LivingFrameEngine.planStill(2, 8_000, opaque, budget)
            assertTrue("opaque=$opaque bytes=${p.bytes}", p.bytes <= budget)
            assertTrue(p.outW >= 2 && p.outH >= 2)
            assertEquals(1, p.frames)
        }
    }

    @Test
    fun displaySteps_noneHasNoBlends() {
        val steps = LivingFrameEngine.displaySteps(
            4, 12, 12, 1f, LivingFrameLoopMode.LOOP, LivingFrameLoopTransition.NONE
        )
        assertEquals(4, steps.size)
        assertTrue(steps.none { it.blended })
    }

    @Test
    fun displaySteps_fadeAddsOrderedSeamBlends() {
        val steps = LivingFrameEngine.displaySteps(
            4, 12, 12, 1f, LivingFrameLoopMode.LOOP, LivingFrameLoopTransition.FADE
        )
        val seam = steps.takeLast(LivingFrameEngine.seamStepCount(12))
        assertEquals(4 + seam.size, steps.size)
        assertTrue(seam.all { it.from == 3 && it.to == 0 })
        assertTrue(seam.all { it.blended && it.mix in 0f..1f })
        assertTrue(seam.zipWithNext().all { (a, b) -> b.mix > a.mix })
    }

    @Test
    fun displaySteps_boomerangIgnoresTransition() {
        val plain = LivingFrameEngine.displaySteps(
            4, 12, 12, 1f, LivingFrameLoopMode.BOOMERANG, LivingFrameLoopTransition.NONE
        )
        val faded = LivingFrameEngine.displaySteps(
            4, 12, 12, 1f, LivingFrameLoopMode.BOOMERANG, LivingFrameLoopTransition.BLUR
        )
        assertEquals(plain, faded)
        assertTrue(faded.none { it.blended })
    }

    @Test
    fun displaySteps_singleFrameHasNothingToFade() {
        val steps = LivingFrameEngine.displaySteps(
            1, 12, 12, 1f, LivingFrameLoopMode.LOOP, LivingFrameLoopTransition.FADE
        )
        assertEquals(1, steps.size)
        assertEquals(0, steps.single().from)
    }

    @Test
    fun displayDuration_includesSeamSteps() {
        assertEquals(
            4 * 83L,
            LivingFrameEngine.displayDurationMs(
                4, 12, 12, 1f, LivingFrameLoopMode.LOOP,
                LivingFrameLoopTransition.NONE
            )
        )
        assertEquals(
            5 * 83L,
            LivingFrameEngine.displayDurationMs(
                4, 12, 12, 1f, LivingFrameLoopMode.LOOP,
                LivingFrameLoopTransition.FADE
            )
        )
    }

    @Test
    fun rotateSteps_startsAtTheSavedPausePoint() {
        val steps = LivingFrameEngine.displaySteps(
            4, 12, 12, 1f, LivingFrameLoopMode.LOOP, LivingFrameLoopTransition.FADE
        )
        val rotated = LivingFrameEngine.rotateSteps(steps, 2)
        assertEquals(steps.size, rotated.size)
        assertEquals(steps[2], rotated.first())
        assertEquals(steps[0], LivingFrameEngine.rotateSteps(steps, 0).first())
        // A stale index from a larger previous loop clamps instead of crashing.
        assertEquals(steps.last(), LivingFrameEngine.rotateSteps(steps, 999).first())
    }

    @Test
    fun nearestStepIndex_findsBlendsAndSurvivesMissingFrames() {
        val steps = LivingFrameEngine.displaySteps(
            4, 12, 12, 1f, LivingFrameLoopMode.LOOP, LivingFrameLoopTransition.FADE
        )
        val seam = steps.last()
        assertEquals(steps.lastIndex, LivingFrameEngine.nearestStepIndex(steps, 3, 0, 0.5f))
        assertEquals(2, LivingFrameEngine.nearestStepIndex(steps, 2, 2, 0f))
        // The loop changed and `to` no longer exists: keep the `from` frame.
        assertEquals(2, LivingFrameEngine.nearestStepIndex(steps, 2, 9, 0f))
        assertEquals(0, LivingFrameEngine.nearestStepIndex(emptyList(), seam.from, seam.to, 0f))
    }

    @Test
    fun cornerRadii_compensateForRenderScale() {
        val r = LivingFrameEngine.cornerRadii(
            cornerDp = 24,
            densityX = 5f,
            densityY = 3f,
            widthPx = 1000,
            heightPx = 600
        )
        assertEquals(120f, r.rx, 0.01f)
        assertEquals(72f, r.ry, 0.01f)
    }

    @Test
    fun cornerRadii_clampToHalfTheOutputAndIgnoreZero() {
        val clamped = LivingFrameEngine.cornerRadii(40, 10f, 10f, 100, 50)
        assertEquals(50f, clamped.rx, 0.01f)
        assertEquals(25f, clamped.ry, 0.01f)
        assertEquals(0f, LivingFrameEngine.cornerRadii(0, 3f, 3f, 100, 100).rx, 0f)
    }

    @Test
    fun densityScale_matchesOutputOverWidget() {
        assertEquals(2f, LivingFrameEngine.densityScale(1f, 400, 200), 0.0001f)
        assertEquals(1f, LivingFrameEngine.densityScale(1f, 200, 200), 0.0001f)
        assertEquals(3f, LivingFrameEngine.densityScale(1.5f, 400, 200), 0.0001f)
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
