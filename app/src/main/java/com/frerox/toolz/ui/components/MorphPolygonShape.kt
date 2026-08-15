package com.frerox.toolz.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

/**
 * Adapts an androidx.graphics.shapes.Morph into a Compose [Shape] so it can
 * be used directly with Modifier.clip(). `progress` is read lazily on every
 * outline request so the shape stays live against an external animation
 * value (e.g. pager scroll offset) without needing to be re-created.
 *
 * Builds the Compose [Path] directly via [Morph.forEachCubic] rather than
 * `Morph.toPath()`, since graphics-shapes moved to a KMP-common core and
 * no longer exposes an android.graphics.Path-returning member on Morph in
 * newer alpha releases.
 *
 * Rather than assuming a fixed normalization (radius-1 circle centered at
 * origin) for the underlying RoundedPolygons — which broke for some
 * MaterialShapes entries and produced a cropped, off-center outline — this
 * measures the *actual* bounds of the generated path at the current
 * progress and fits those bounds to the target size every time. This is
 * slightly more work per outline but is correct regardless of how any
 * given shape or Morph is normalized internally.
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: () -> Float,
) : Shape {

    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        var first = true

        morph.forEachCubic(progress()) { cubic ->
            if (first) {
                path.moveTo(cubic.anchor0X, cubic.anchor0Y)
                first = false
            }
            path.cubicTo(
                cubic.control0X, cubic.control0Y,
                cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y,
            )
        }
        path.close()

        // Fit the path's actual bounds to the target size instead of
        // assuming a fixed normalization. This is the part that makes the
        // shape robust to whatever coordinate range this particular Morph
        // happens to produce.
        val bounds = path.getBounds()
        val boundsWidth = bounds.width.takeIf { it > 0f } ?: 1f
        val boundsHeight = bounds.height.takeIf { it > 0f } ?: 1f

        matrix.reset()
        matrix.translate(-bounds.left, -bounds.top, 0f)
        matrix.scale(size.width / boundsWidth, size.height / boundsHeight, 1f)
        path.transform(matrix)

        return Outline.Generic(path)
    }
}