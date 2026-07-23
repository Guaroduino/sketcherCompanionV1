package com.sketcher.sketchercompanionv1.spatial

import android.graphics.RectF
import com.sketcher.sketchercompanionv1.LayerElement

/**
 * High-performance 2D QuadTree spatial index for accelerating 
 * spatial queries (Eraser hit-testing, CAD Snapping, Area Selection) from O(N) to O(log N).
 */
class SpatialIndex<T : Any>(
    private val bounds: RectF = RectF(-100000f, -100000f, 100000f, 100000f),
    private val maxObjectsPerNode: Int = 16,
    private val maxDepth: Int = 8,
    private val currentDepth: Int = 0
) {
    private class Entry<T>(val bounds: RectF, val item: T)

    private val entries = ArrayList<Entry<T>>()
    private var children: Array<SpatialIndex<T>>? = null

    fun clear() {
        entries.clear()
        children?.forEach { it.clear() }
        children = null
    }

    fun insert(itemBounds: RectF, item: T) {
        if (!RectF.intersects(bounds, itemBounds)) {
            return
        }

        if (children != null) {
            val index = getSubnodeIndex(itemBounds)
            if (index != -1) {
                children!![index].insert(itemBounds, item)
                return
            }
        }

        entries.add(Entry(itemBounds, item))

        if (entries.size > maxObjectsPerNode && currentDepth < maxDepth && children == null) {
            subdivide()
            var i = 0
            while (i < entries.size) {
                val entry = entries[i]
                val index = getSubnodeIndex(entry.bounds)
                if (index != -1) {
                    entries.removeAt(i)
                    children!![index].insert(entry.bounds, entry.item)
                } else {
                    i++
                }
            }
        }
    }

    private fun subdivide() {
        val midX = bounds.centerX()
        val midY = bounds.centerY()

        val nw = RectF(bounds.left, bounds.top, midX, midY)
        val ne = RectF(midX, bounds.top, bounds.right, midY)
        val sw = RectF(bounds.left, midY, midX, bounds.bottom)
        val se = RectF(midX, midY, bounds.right, bounds.bottom)

        children = arrayOf(
            SpatialIndex(nw, maxObjectsPerNode, maxDepth, currentDepth + 1),
            SpatialIndex(ne, maxObjectsPerNode, maxDepth, currentDepth + 1),
            SpatialIndex(sw, maxObjectsPerNode, maxDepth, currentDepth + 1),
            SpatialIndex(se, maxObjectsPerNode, maxDepth, currentDepth + 1)
        )
    }

    private fun getSubnodeIndex(itemBounds: RectF): Int {
        val midX = bounds.centerX()
        val midY = bounds.centerY()

        val topQuadrant = itemBounds.bottom <= midY
        val bottomQuadrant = itemBounds.top >= midY

        if (itemBounds.right <= midX) {
            if (topQuadrant) return 0 // NW
            if (bottomQuadrant) return 2 // SW
        } else if (itemBounds.left >= midX) {
            if (topQuadrant) return 1 // NE
            if (bottomQuadrant) return 3 // SE
        }
        return -1 // Intersects multiple quadrants, keep in parent
    }

    fun query(searchArea: RectF, result: MutableSet<T>) {
        if (!RectF.intersects(bounds, searchArea)) {
            return
        }

        for (i in 0 until entries.size) {
            val entry = entries[i]
            if (RectF.intersects(entry.bounds, searchArea)) {
                result.add(entry.item)
            }
        }

        val childNodes = children ?: return
        val index = getSubnodeIndex(searchArea)
        if (index != -1) {
            childNodes[index].query(searchArea, result)
        } else {
            for (child in childNodes) {
                child.query(searchArea, result)
            }
        }
    }

    fun queryRadius(cx: Float, cy: Float, radius: Float, result: MutableSet<T>) {
        val searchArea = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        query(searchArea, result)
    }
}
