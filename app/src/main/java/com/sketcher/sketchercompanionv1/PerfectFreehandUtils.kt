package com.sketcher.sketchercompanionv1

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

// --- Vec2 Implementation ---
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Vec2(x / scalar, y / scalar)
    operator fun unaryMinus() = Vec2(-x, -y)
}

object PerfectFreehandUtils {
    
    // Constants
    const val PI = Math.PI.toFloat()
    const val RATE_OF_PRESSURE_CHANGE = 0.275f

    // Vector Math
    fun add(a: Vec2, b: Vec2) = a + b
    fun sub(a: Vec2, b: Vec2) = a - b
    fun mul(a: Vec2, n: Float) = a * n
    fun div(a: Vec2, n: Float) = a / n
    fun neg(a: Vec2) = -a
    
    fun per(a: Vec2) = Vec2(-a.y, a.x)
    
    fun dpr(a: Vec2, b: Vec2) = a.x * b.x + a.y * b.y
    
    fun len(a: Vec2) = hypot(a.x, a.y)
    
    fun dist(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
    
    fun dist2(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
    
    fun uni(a: Vec2): Vec2 {
        val l = len(a)
        return if (l != 0f) a / l else Vec2(0f, 0f)
    }
    
    fun lrp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun lrp(a: Vec2, b: Vec2, t: Float) = add(a, mul(sub(b, a), t))
    
    fun prj(a: Vec2, b: Vec2, c: Float) = add(a, mul(b, c))
    
    fun rotAround(a: Vec2, c: Vec2, r: Float): Vec2 {
        val s = sin(r)
        val cVal = cos(r)
        
        val px = a.x - c.x
        val py = a.y - c.y
        
        val nx = px * cVal - py * s
        val ny = px * s + py * cVal
        
        return Vec2(nx + c.x, ny + c.y)
    }

    // --- Helpers from logic ---

    fun simulatePressure(prevPressure: Float, distance: Float, size: Float): Float {
        val sp = min(1f, distance / size)
        val rp = min(1f, 1f - sp)
        return min(1f, prevPressure + (rp - prevPressure) * (sp * RATE_OF_PRESSURE_CHANGE))
    }

    fun getStrokeRadius(size: Float, thinning: Float, pressure: Float, easing: (Float) -> Float = { it }): Float {
        return size * easing(0.5f - thinning * (0.5f - pressure))
    }
}

