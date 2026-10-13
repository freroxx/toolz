/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Exact geometry of the Toolz mark, in a 1080-unit square.
 *
 * Every number here was fitted to the original 1080px renders (max edge error < 0.5px),
 * so the mark is resolution independent and needs no bitmap layers.
 *
 *   rim     rounded regular octagon, flat colour
 *   body    smaller rounded octagon, eased linear gradient at 56 degrees
 *   disc    circle
 *   capsule rotated stadium drawn over disc + body in the gradient's start colour
 *   swoosh  disc minus capsule as one outline (glyph-only mode, monochrome icon)
 *
 * The same constants produce res/drawable/ic_launcher_foreground.xml.
 */
internal object MarkGeometry {
    const val CENTER = 540f
    /** Edge-to-edge extent of the rim octagon. The mark is scaled so this fills the box. */
    const val SPAN = 972f
    const val RIM_WIDTH = 35f

    private const val RIM_APOTHEM = 454.73
    private const val RIM_CORNER = 105.23
    private const val BODY_APOTHEM = 419.69
    private const val BODY_CORNER = 77.12
    private const val DISC_RADIUS = 328.0

    private const val CAPSULE_X1 = 419.9
    private const val CAPSULE_Y1 = 282.6
    private const val CAPSULE_X2 = 526.1
    private const val CAPSULE_Y2 = 519.4
    private const val CAPSULE_RADIUS = 131.7

    const val GRADIENT_DEGREES = 56.0
    private const val GRADIENT_LO = -440.8
    private const val GRADIENT_HI = 439.5

    val gradientAxis: Offset = Offset(
        cos(rad(GRADIENT_DEGREES)).toFloat(),
        sin(rad(GRADIENT_DEGREES)).toFloat(),
    )
    val gradientStart: Offset = Offset(CENTER, CENTER) + gradientAxis * GRADIENT_LO.toFloat()
    val gradientEnd: Offset = Offset(CENTER, CENTER) + gradientAxis * GRADIENT_HI.toFloat()

    val rim: Path by lazy { roundedOctagon(RIM_APOTHEM, RIM_CORNER) }
    val body: Path by lazy { roundedOctagon(BODY_APOTHEM, BODY_CORNER) }
    val disc: Path by lazy { Path().apply { addOval(Rect(Offset(CENTER, CENTER), DISC_RADIUS.toFloat())) } }
    val capsule: Path by lazy { buildCapsule() }
    val swoosh: Path by lazy { buildSwoosh() }

    private fun rad(deg: Double) = deg * PI / 180.0
    private fun deg(rad: Double) = rad * 180.0 / PI

    /** Regular octagon, vertices on the axes, every corner a circular arc. */
    private fun roundedOctagon(apothem: Double, corner: Double): Path {
        val vertex = apothem / cos(rad(22.5))
        val arcCentre = vertex - corner / sin(rad(67.5))
        return Path().apply {
            for (k in 0 until 8) {
                val phi = 45.0 * k
                val c = Offset(
                    (CENTER + arcCentre * cos(rad(phi))).toFloat(),
                    (CENTER + arcCentre * sin(rad(phi))).toFloat(),
                )
                arcTo(Rect(c, corner.toFloat()), (phi - 22.5).toFloat(), 45f, forceMoveTo = k == 0)
            }
            close()
        }
    }

    private class Axis(val ux: Double, val uy: Double) {
        val nx get() = -uy // screen-left normal
        val ny get() = ux
    }

    private fun capsuleAxis(): Axis {
        val dx = CAPSULE_X2 - CAPSULE_X1
        val dy = CAPSULE_Y2 - CAPSULE_Y1
        val l = hypot(dx, dy)
        return Axis(dx / l, dy / l)
    }

    private fun buildCapsule(): Path {
        val a = capsuleAxis()
        val r = CAPSULE_RADIUS
        val nAngle = deg(atan2(a.ny, a.nx)).toFloat()
        fun side(x: Double, y: Double, sign: Double) =
            Offset((x + sign * r * a.nx).toFloat(), (y + sign * r * a.ny).toFloat())
        return Path().apply {
            val start = side(CAPSULE_X1, CAPSULE_Y1, +1.0)
            moveTo(start.x, start.y)
            val bottom = side(CAPSULE_X2, CAPSULE_Y2, +1.0)
            lineTo(bottom.x, bottom.y)
            arcTo(Rect(Offset(CAPSULE_X2.toFloat(), CAPSULE_Y2.toFloat()), r.toFloat()), nAngle, -180f, false)
            val top = side(CAPSULE_X1, CAPSULE_Y1, -1.0)
            lineTo(top.x, top.y)
            arcTo(Rect(Offset(CAPSULE_X1.toFloat(), CAPSULE_Y1.toFloat()), r.toFloat()), nAngle + 180f, -180f, false)
            close()
        }
    }

    /** Disc with the capsule cut out: one closed outline, no overdraw. */
    private fun buildSwoosh(): Path {
        val a = capsuleAxis()
        val r = CAPSULE_RADIUS
        val c = CENTER.toDouble()

        // First point where a capsule side line enters the disc.
        fun entry(sign: Double): Offset {
            val ox = CAPSULE_X1 + sign * r * a.nx
            val oy = CAPSULE_Y1 + sign * r * a.ny
            val qx = ox - c
            val qy = oy - c
            val b = qx * a.ux + qy * a.uy
            val cc = qx * qx + qy * qy - DISC_RADIUS * DISC_RADIUS
            val s = -b - sqrt(b * b - cc)
            return Offset((ox + s * a.ux).toFloat(), (oy + s * a.uy).toFloat())
        }

        val left = entry(+1.0)
        val right = entry(-1.0)
        fun angleOnDisc(p: Offset) = deg(atan2((p.y - c).toDouble(), (p.x - c).toDouble()))
        val aRight = angleOnDisc(right)
        val aLeft = angleOnDisc(left)
        val sweep = (((aLeft - aRight) % 360.0) + 360.0) % 360.0 // clockwise, the long way round

        val nAngle = deg(atan2(a.ny, a.nx)).toFloat()
        return Path().apply {
            moveTo(right.x, right.y)
            arcTo(Rect(Offset(CENTER, CENTER), DISC_RADIUS.toFloat()), aRight.toFloat(), sweep.toFloat(), false)
            lineTo((CAPSULE_X2 + r * a.nx).toFloat(), (CAPSULE_Y2 + r * a.ny).toFloat())
            arcTo(Rect(Offset(CAPSULE_X2.toFloat(), CAPSULE_Y2.toFloat()), r.toFloat()), nAngle, -180f, false)
            lineTo(right.x, right.y)
            close()
        }
    }
}
