package com.sketcher.sketchercompanionv1

import kotlin.math.sqrt

object StrokePredictor {
    
    /**
     * Calculates a predicted point based on a smoothed velocity history.
     * Uses linear extrapolation with a damping factor over a constant time window.
     */
    fun getPredictedPoint(
        points: List<StrokePoint>, 
        predictionLatencyMillis: Long = 35
    ): StrokePoint? {
        if (points.size < 3) return null

        val p0 = points.last() // Punto actual

        // 1. Buscar hacia atrás para encontrar vectores estables en el tiempo
        var p1 = points[points.size - 2]
        var p2 = points.first()

        for (i in points.indices.reversed()) {
            val dt = p0.timestamp - points[i].timestamp
            if (dt in 10..25) p1 = points[i]
            if (dt > 25) { p2 = points[i]; break }
        }

        if (p1 === p0 || p1 === p2) return null

        // 2. Calcular vectores de movimiento
        val v2x = p0.x - p1.x
        val v2y = p0.y - p1.y
        val dt2 = (p0.timestamp - p1.timestamp).toFloat().coerceAtLeast(1f)

        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y

        val vx = v2x / dt2
        val vy = v2y / dt2
        val speed = kotlin.math.sqrt(vx * vx + vy * vy)

        // Zona muerta: si casi no se mueve, no predecimos
        if (speed < 0.05f) return null 

        // 3. Producto Punto para Detección de Curvatura / Ángulo
        val mag1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)
        val mag2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)

        var curveDampening = 1.0f
        if (mag1 > 0f && mag2 > 0f) {
            val dot = (v1x * v2x + v1y * v2y)
            val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
            
            // cosTheta == 1.0 es línea recta. cosTheta < 0 es un giro de más de 90 grados.
            // Mapeamos de 0.5 (giro moderado) a 1.0 (recto) hacia un multiplicador de 0.0 a 1.0
            curveDampening = ((cosTheta - 0.5f) / 0.5f).coerceIn(0f, 1f)
        }

        // 4. Amortiguación de velocidad para trazos cortos/lentos
        // Reduce la predicción hasta un 20% si la velocidad es muy baja
        val speedDampening = (speed / 1.0f).coerceIn(0.2f, 1.0f)

        // 5. Extrapolación Final
        val effectiveMillis = predictionLatencyMillis * curveDampening * speedDampening
        val finalDamping = 0.85f // Suavizado general

        val predX = p0.x + (vx * effectiveMillis * finalDamping)
        val predY = p0.y + (vy * effectiveMillis * finalDamping)

        return StrokePoint(predX, predY, p0.pressure, p0.timestamp + effectiveMillis.toLong())
    }
}
