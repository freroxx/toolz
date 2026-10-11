package com.frerox.toolz.widget

import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLagLevel
import com.frerox.toolz.data.livingframes.LivingFrameLagMeter
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivingFrameEngineTest {

    @Test
    fun intervalMs_clampsToSupportedRange() {
        assertEquals(1000L, LivingFrameEngine.intervalMs(1))
        assertEquals(1000L / 15, LivingFrameEngine.intervalMs(15))
        assertEquals(1000L / 30, LivingFrameEngine.intervalMs(30))
        assertEquals(1000L / 30, LivingFrameEngine.intervalMs(99))
    }

    @Test
    fun loop_advancesAndWraps() {
        val seq = LivingFrameEngine.sequence(0, 5, 3, LivingFrameLoopMode.LOOP)
        assertEquals(listOf(0, 1, 2, 0, 1), seq)
    }

    @Test
    fun once_holdsOnLastFrame() {
        val seq = LivingFrameEngine.sequence(0, 5, 3, LivingFrameLoopMode.ONCE)
        assertEquals(listOf(0, 1, 2, 2, 2), seq)
        assertTrue(LivingFrameEngine.isFinished(2, 3, LivingFrameLoopMode.ONCE))
    }

    @Test
    fun boomerang_reversesAtEnds() {
        val seq = LivingFrameEngine.sequence(0, 6, 3, LivingFrameLoopMode.BOOMERANG)
        assertEquals(listOf(0, 1, 2, 1, 0, 1), seq)
    }

    @Test
    fun fpsPresets_includeThirtyExtreme() {
        assertTrue(LivingFrameFps.PRESETS.contains(30))
        assertEquals(30, LivingFrameFps.MAX)
        assertEquals(33L, LivingFrameEngine.intervalMs(30))
    }

    @Test
    fun lagMeter_marksHighFpsExperimental() {
        val calm = LivingFrameLagMeter.estimate(10, 40_000L)
        assertEquals(LivingFrameLagLevel.FINE, calm.level)
        val hot = LivingFrameLagMeter.estimate(24, 60_000L)
        assertEquals(LivingFrameLagLevel.LIKELY_LAG, hot.level)
        assertTrue(hot.kilobytesPerSecond > calm.kilobytesPerSecond)
    }

    @Test
    fun slideshow_defaultsAreSane() {
        val frame = com.frerox.toolz.data.livingframes.LivingFrame(
            id = "x", name = "s",
            type = com.frerox.toolz.data.livingframes.LivingFrameType.SLIDESHOW,
            frameCount = 5, width = 100, height = 100
        )
        assertEquals(60, frame.slideshowIntervalMin)
        assertEquals(false, frame.shuffle)
    }

    @Test
    fun slideshow_intervalOptions_coverMinutesToDaily() {
        val opts = com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper.SLIDESHOW_INTERVALS_MIN
        assertTrue(opts.contains(15))
        assertTrue(opts.contains(1440))
        assertTrue(opts.all { it in 1..1440 })
        assertEquals(
            "1 hour",
            com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper.slideshowIntervalLabel(60)
        )
    }
}
