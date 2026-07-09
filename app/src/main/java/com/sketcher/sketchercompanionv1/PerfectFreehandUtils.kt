package com.sketcher.sketchercompanionv1

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

// --- Vec2 Implementation (Mutable for Arena Allocator) ---
class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float): Vec2 { x = nx; y = ny; return this }
    fun set(other: Vec2): Vec2 { x = other.x; y = other.y; return this }
    
    fun add(other: Vec2): Vec2 { x += other.x; y += other.y; return this }
    fun sub(other: Vec2): Vec2 { x -= other.x; y -= other.y; return this }
    fun mul(scalar: Float): Vec2 { x *= scalar; y *= scalar; return this }
    fun div(scalar: Float): Vec2 { x /= scalar; y /= scalar; return this }
    fun neg(): Vec2 { x = -x; y = -y; return this }
    fun per(): Vec2 { val tx = x; x = -y; y = tx; return this }
    fun uni(): Vec2 {
        val l = hypot(x, y)
        if (l != 0f) { x /= l; y /= l } else { x = 0f; y = 0f }
        return this
    }
    
    // Non-mutating math that requires an out parameter (from the pool)
    fun add(other: Vec2, out: Vec2): Vec2 = out.set(this).add(other)
    fun sub(other: Vec2, out: Vec2): Vec2 = out.set(this).sub(other)
    fun mul(scalar: Float, out: Vec2): Vec2 = out.set(this).mul(scalar)
    fun div(scalar: Float, out: Vec2): Vec2 = out.set(this).div(scalar)
    fun neg(out: Vec2): Vec2 = out.set(this).neg()
    fun per(out: Vec2): Vec2 = out.set(this).per()
    fun uni(out: Vec2): Vec2 = out.set(this).uni()
}

// Arena Allocator for Vec2
class Vec2Pool(private val initialCapacity: Int = 1000) {
    private val pool = ArrayList<Vec2>(initialCapacity)
    private var index = 0

    init {
        for (i in 0 until initialCapacity) {
            pool.add(Vec2())
        }
    }

    fun obtain(): Vec2 {
        if (index >= pool.size) {
            pool.add(Vec2())
        }
        return pool[index++]
    }

    fun obtain(x: Float, y: Float): Vec2 = obtain().set(x, y)
    fun obtain(other: Vec2): Vec2 = obtain().set(other)

    fun reset() {
        index = 0
    }
}

object PerfectFreehandUtils {
    // Constants
    const val PI = Math.PI.toFloat()
    const val RATE_OF_PRESSURE_CHANGE = 0.275f

    // Vector Math (Non-allocating, uses out parameters or mutating methods)
    fun dpr(a: Vec2, b: Vec2) = a.x * b.x + a.y * b.y
    fun len(a: Vec2) = hypot(a.x, a.y)
    fun dist(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)
    
    fun dist2(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
    
    fun lrp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun lrp(a: Vec2, b: Vec2, t: Float, out: Vec2): Vec2 {
        out.x = a.x + (b.x - a.x) * t
        out.y = a.y + (b.y - a.y) * t
        return out
    }
    
    fun prj(a: Vec2, b: Vec2, c: Float, out: Vec2): Vec2 {
        out.x = a.x + (b.x * c)
        out.y = a.y + (b.y * c)
        return out
    }
    
    fun rotAround(a: Vec2, c: Vec2, r: Float, out: Vec2): Vec2 {
        val s = sin(r)
        val cVal = cos(r)
        
        val px = a.x - c.x
        val py = a.y - c.y
        
        val nx = px * cVal - py * s
        val ny = px * s + py * cVal
        
        out.x = nx + c.x
        out.y = ny + c.y
        return out
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

object StrokeEasings {
    fun identity(t: Float): Float = t
    fun easeInOut(t: Float): Float = t * (2 - t)
    fun easeOutCubic(t: Float): Float {
        var tm = t
        tm--
        return tm * tm * tm + 1f
    }
}
