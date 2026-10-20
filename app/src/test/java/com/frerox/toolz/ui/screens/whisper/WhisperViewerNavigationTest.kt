/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure index math for the segmented Daily Ups viewer. Segment sizes are enough:
 * the UI never builds empty segments, and these tests pin the boundary behavior
 * that the horizontal pager and the progress bars rely on.
 */
class WhisperViewerNavigationTest {

    @Test
    fun nextRollsIntoTheNextSegment() {
        val sizes = listOf(2, 1, 3)

        assertEquals(0 to 1, viewerNextPosition(sizes, 0, 0))
        assertEquals(1 to 0, viewerNextPosition(sizes, 0, 1))
        assertEquals(2 to 0, viewerNextPosition(sizes, 1, 0))
        assertEquals(2 to 1, viewerNextPosition(sizes, 2, 0))
        assertEquals(2 to 2, viewerNextPosition(sizes, 2, 1))
    }

    @Test
    fun nextAtTheEndReturnsNullToClose() {
        assertNull(viewerNextPosition(listOf(1, 2), 1, 1))
        assertNull(viewerNextPosition(listOf(2, 2), 2, 0))
        assertNull(viewerNextPosition(emptyList(), 0, 0))
    }

    @Test
    fun prevAtSegmentStartMovesToPreviousSegmentsLastStory() {
        assertEquals(0 to 1, viewerPrevPosition(listOf(2, 2), 1, 0))
        assertEquals(1 to 0, viewerPrevPosition(listOf(1, 1, 3), 2, 0))
        assertEquals(1 to 0, viewerPrevPosition(listOf(1, 2, 1), 1, 1))
    }

    @Test
    fun prevAtGlobalStartClamps() {
        assertNull(viewerPrevPosition(listOf(2, 1), 0, 0))
        assertNull(viewerPrevPosition(emptyList(), 0, 0))
        assertNull(viewerPrevPosition(listOf(1), 1, 0))
    }

    @Test
    fun flatPositionIsEarlierSegmentSizesPlusStoryIndex() {
        val sizes = listOf(2, 3, 1)

        assertEquals(0, viewerFlatPosition(sizes, 0, 0))
        assertEquals(1, viewerFlatPosition(sizes, 0, 1))
        assertEquals(2, viewerFlatPosition(sizes, 1, 0))
        assertEquals(4, viewerFlatPosition(sizes, 1, 2))
        assertEquals(5, viewerFlatPosition(sizes, 2, 0))
        assertEquals(1 to 2, viewerPositionAtFlat(sizes, 4))
    }

    @Test
    fun oneStorySegmentsRoundTripThroughFlatPositions() {
        val sizes = listOf(1, 1, 1)

        sizes.indices.forEach { segment ->
            val position = viewerPositionAtFlat(sizes, segment)
            assertEquals(segment to 0, position)
            val (segmentIndex, storyIndex) = requireNotNull(position)
            assertEquals(segment, viewerFlatPosition(sizes, segmentIndex, storyIndex))
        }
        assertNull(viewerPositionAtFlat(sizes, sizes.size))
    }
}
