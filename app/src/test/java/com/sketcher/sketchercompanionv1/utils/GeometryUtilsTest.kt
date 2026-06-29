package com.sketcher.sketchercompanionv1.utils

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeometryUtilsTest {

    @Test
    fun testFindLineLineIntersection_Intersecting() {
        val a = PointF(0f, 0f)
        val b = PointF(10f, 10f)
        val c = PointF(0f, 10f)
        val d = PointF(10f, 0f)

        val intersection = GeometryUtils.findLineLineIntersection(a, b, c, d)
        assertNotNull("Should find intersection", intersection)
        assertEquals("X intersection coordinate", 5f, intersection!!.x, 1e-4f)
        assertEquals("Y intersection coordinate", 5f, intersection.y, 1e-4f)
    }

    @Test
    fun testFindLineLineIntersection_Parallel() {
        val a = PointF(0f, 0f)
        val b = PointF(10f, 0f)
        val c = PointF(0f, 5f)
        val d = PointF(10f, 5f)

        val intersection = GeometryUtils.findLineLineIntersection(a, b, c, d)
        assertNull("Parallel lines should not intersect", intersection)
    }

    @Test
    fun testFindLineLineIntersection_NoOverlap() {
        val a = PointF(0f, 0f)
        val b = PointF(5f, 5f)
        val c = PointF(10f, 0f)
        val d = PointF(10f, 5f)

        val intersection = GeometryUtils.findLineLineIntersection(a, b, c, d)
        assertNull("Lines don't overlap within segments", intersection)
    }

    @Test
    fun testFindLineCircleIntersections_TwoPoints() {
        val a = PointF(-10f, 0f)
        val b = PointF(10f, 0f)
        val c = PointF(0f, 0f)
        val r = 5f

        val pts = GeometryUtils.findLineCircleIntersections(a, b, c, r)
        assertEquals("Should find exactly 2 intersections", 2, pts.size)
        // Order might be dependent on t, but t1=-t2
        val xCoords = pts.map { it.x }.sorted()
        assertEquals(-5f, xCoords[0], 1e-4f)
        assertEquals(5f, xCoords[1], 1e-4f)
    }

    @Test
    fun testFindLineCircleIntersections_NoIntersection() {
        val a = PointF(-10f, 10f)
        val b = PointF(10f, 10f)
        val c = PointF(0f, 0f)
        val r = 5f

        val pts = GeometryUtils.findLineCircleIntersections(a, b, c, r)
        assertTrue("Should find no intersections", pts.isEmpty())
    }

    @Test
    fun testFindCircleCircleIntersections_TwoPoints() {
        val c1 = PointF(0f, 0f)
        val r1 = 5f
        val c2 = PointF(6f, 0f)
        val r2 = 5f

        // Intersections should be at X = 3
        // Y^2 = 5^2 - 3^2 = 16 -> Y = +/-4
        val pts = GeometryUtils.findCircleCircleIntersections(c1, r1, c2, r2)
        assertEquals("Should find 2 intersections", 2, pts.size)
        val yCoords = pts.map { it.y }.sorted()
        assertEquals(-4f, yCoords[0], 1e-4f)
        assertEquals(4f, yCoords[1], 1e-4f)
        assertEquals(3f, pts[0].x, 1e-4f)
    }

    @Test
    fun testGetArcParams_Valid() {
        val p1 = PointF(0f, 5f)
        val p2 = PointF(5f, 0f)
        val p3 = PointF(0f, -5f)

        // Circle centered at (0, 0) with radius 5
        val arc = GeometryUtils.getArcParams(p1, p2, p3)
        assertNotNull("Should reconstruct arc parameters", arc)
        assertEquals("Center X", 0f, arc!!.center.x, 1e-3f)
        assertEquals("Center Y", 0f, arc.center.y, 1e-3f)
        assertEquals("Radius", 5f, arc.radius, 1e-3f)
        assertEquals("Start Angle (90 degrees)", 90f, arc.startAngleDeg, 1e-2f)
        assertEquals("Sweep Angle (-180 degrees)", -180f, arc.sweepAngleDeg, 1e-2f)
    }

    @Test
    fun testGetArcParams_Collinear() {
        val p1 = PointF(0f, 0f)
        val p2 = PointF(5f, 5f)
        val p3 = PointF(10f, 10f)

        val arc = GeometryUtils.getArcParams(p1, p2, p3)
        assertNull("Collinear points should return null arc params", arc)
    }

    @Test
    fun testDistanceToSegment() {
        val a = PointF(0f, 0f)
        val b = PointF(10f, 0f)
        val p = PointF(5f, 5f)

        val dist = GeometryUtils.distanceToSegment(p, a, b)
        assertEquals("Perpendicular distance should be 5", 5f, dist, 1e-4f)

        val pPastB = PointF(15f, 5f)
        // Distance to B (10, 0) -> sqrt(5^2 + 5^2) = sqrt(50) = 7.071
        val distPastB = GeometryUtils.distanceToSegment(pPastB, a, b)
        assertEquals("Distance past endpoint B", kotlin.math.sqrt(50f), distPastB, 1e-4f)
    }

    @Test
    fun testClosestPointOnSegment() {
        val a = PointF(0f, 0f)
        val b = PointF(10f, 0f)
        val p = PointF(4f, 8f)

        val closest = GeometryUtils.closestPointOnSegment(p, a, b)
        assertEquals("Closest point X", 4f, closest.x, 1e-4f)
        assertEquals("Closest point Y", 0f, closest.y, 1e-4f)
    }
}
