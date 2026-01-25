package com.skecher.sketchercompanionv1



import android.content.Context

import android.graphics.Canvas

import android.graphics.Matrix

import android.view.View

import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

import androidx.ink.strokes.Stroke



class DryInkView(context: Context) : View(context) {

    private val strokes = mutableListOf<Stroke>()

    private val renderer = CanvasStrokeRenderer.create()

// Matriz identidad para dibujar en píxeles puros (1:1 con lo que dibujaste)

    private val identityMatrix = Matrix()



    private val undoStack = mutableListOf<List<Stroke>>()

    private val redoStack = mutableListOf<List<Stroke>>()



    init {

// Renderizado por GPU para máxima fluidez

        setLayerType(LAYER_TYPE_HARDWARE, null)

    }



    fun addStrokes(newStrokes: Collection<Stroke>) {

        if (newStrokes.isNotEmpty()) {

            undoStack.add(strokes.toList())

            redoStack.clear()

            strokes.addAll(newStrokes)

            invalidate()

        }

    }



    fun undo() {

        if (undoStack.isNotEmpty()) {

            redoStack.add(strokes.toList())

            val previous = undoStack.removeAt(undoStack.lastIndex)

            strokes.clear()

            strokes.addAll(previous)

            invalidate()

        }

    }



    fun redo() {

        if (redoStack.isNotEmpty()) {

            undoStack.add(strokes.toList())

            val next = redoStack.removeAt(redoStack.lastIndex)

            strokes.clear()

            strokes.addAll(next)

            invalidate()

        }

    }



    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        strokes.forEach { stroke ->

            renderer.draw(canvas, stroke, identityMatrix)

        }

    }

}