package com.sketcher.sketchercompanionv1

import kotlin.math.sqrt

object StrokePredictor {
    
    /**
     * Calculates a predicted point based on a smoothed velocity history.
     * Uses linear extrapolation with a damping factor over a constant time window.
     */
    fun getPredictedPoint(
        points: List<StrokePoint>, 
        predictionLatencyMillis: Long = 15,
        currentZoom: Float = 1.0f
    ): StrokePoint? {
        if (points.size < 4) return null

        val p0 = points[points.size - 1] // Actual
        val p1 = points[points.size - 2] // Anterior inmediato
        val p2 = points[points.size - 3] // Tras-anterior inmediato

        // 1. Detección de Zig-Zag (Instantánea por Hardware)
        // Usamos los últimos 3 eventos puros para no perder esquinas rápidas
        val rawV1x = p0.x - p1.x
        val rawV1y = p0.y - p1.y
        val rawMag1 = kotlin.math.sqrt(rawV1x * rawV1x + rawV1y * rawV1y)

        val rawV2x = p1.x - p2.x
        val rawV2y = p1.y - p2.y
        val rawMag2 = kotlin.math.sqrt(rawV2x * rawV2x + rawV2y * rawV2y)

        var zigZagDampening = 1.0f
        if (rawMag1 > 0f && rawMag2 > 0f) {
            val dot = (rawV1x * rawV2x + rawV1y * rawV2y)
            val cosTheta = (dot / (rawMag1 * rawMag2)).coerceIn(-1f, 1f)
            
            // Para pantallas de 60Hz los puntos están más separados.
            // Hacemos la detección de esquinas más estricta para evitar "ghost lines".
            // Si cosTheta baja de 0.85 (~31 grados), empezamos a reducir fuertemente.
            // Si baja de 0.5 (~60 grados), la predicción se anula por completo.
            zigZagDampening = ((cosTheta - 0.5f) / 0.35f).coerceIn(0f, 1f)
        }

        // Si es una esquina, apagamos la predicción en este frame para evitar la línea fantasma
        if (zigZagDampening < 0.01f) return null

        // 2. Cálculo de Velocidad Estable (Promediada en el tiempo)
        var pStable = p1
        for (i in points.indices.reversed()) {
            val dt = p0.timestamp - points[i].timestamp
            if (dt in 10..25) { pStable = points[i]; break }
        }

        val dtStable = (p0.timestamp - pStable.timestamp).toFloat().coerceAtLeast(1f)
        val vx = (p0.x - pStable.x) / dtStable
        val vy = (p0.y - pStable.y) / dtStable
        
        val worldSpeed = kotlin.math.sqrt(vx * vx + vy * vy)
        val screenSpeed = worldSpeed * currentZoom

        if (screenSpeed < 0.05f) return null 

        // 3. Amortiguación Dinámica
        val speedDampening = (screenSpeed / 1.5f).coerceIn(0.1f, 1.0f)
        
        // Multiplicador final
        val effectiveMillis = predictionLatencyMillis * zigZagDampening * speedDampening
        val finalDamping = 0.85f

        val predDistanceX = vx * effectiveMillis * finalDamping
        val predDistanceY = vy * effectiveMillis * finalDamping

        // 4. LÍMITE ANTI-OVERSHOOT (Clamp)
        // Evita que la predicción salga "disparada" si hacemos un trazo cortito muy rápido.
        // La distancia predicha no debe exceder un múltiplo de la distancia estable recorrida.
        val maxExtrapolation = kotlin.math.sqrt((p0.x - pStable.x)*(p0.x - pStable.x) + (p0.y - pStable.y)*(p0.y - pStable.y)) * 1.8f
        val predMag = kotlin.math.sqrt(predDistanceX * predDistanceX + predDistanceY * predDistanceY)

        var finalX = p0.x + predDistanceX
        var finalY = p0.y + predDistanceY

        if (predMag > maxExtrapolation && predMag > 0.01f) {
            val scale = maxExtrapolation / predMag
            finalX = p0.x + predDistanceX * scale
            finalY = p0.y + predDistanceY * scale
        }

        // 5. Extrapolación de presión
        val dp = (p0.pressure - pStable.pressure) / dtStable
        val predPressure = (p0.pressure + (dp * effectiveMillis * finalDamping)).coerceIn(0f, 1f)

        return StrokePoint(finalX, finalY, predPressure, p0.timestamp + effectiveMillis.toLong())
    }
}