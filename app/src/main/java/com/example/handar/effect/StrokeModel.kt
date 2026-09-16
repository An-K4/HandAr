package com.example.handar.effect

import android.graphics.PointF

class StrokeModel {
    private val lock = Any()
    private val _points = mutableListOf<PointF>()

    fun addPoint(normX: Float, normY: Float) {
        synchronized(lock) { _points.add(PointF(normX, normY)) }
    }

    fun snapshot(): List<PointF> = synchronized(lock) { _points.toList() }

    fun clear() {
        synchronized(lock) { _points.clear() }
    }
}