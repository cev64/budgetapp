package com.personal.budget.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** Widgets can't draw paths, so the large size's net-worth sparkline is a small bitmap. */
object SparklineBitmap {
    fun render(values: List<Double>, widthPx: Int, heightPx: Int, color: Int): Bitmap? {
        if (values.size < 2 || widthPx <= 0 || heightPx <= 0) return null
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val pad = heightPx * .12f
        fun x(i: Int) = widthPx * i / (values.size - 1).toFloat()
        fun y(v: Double) = (pad + (hi - v) / span * (heightPx - 2 * pad)).toFloat()
        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) {
                val mx = (x(i - 1) + x(i)) / 2
                cubicTo(mx, y(values[i - 1]), mx, y(values[i]), x(i), y(values[i]))
            }
        }
        val area = Path(line).apply {
            lineTo(widthPx.toFloat(), heightPx.toFloat())
            lineTo(0f, heightPx.toFloat())
            close()
        }
        val alpha = color and 0x00FFFFFF
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), alpha or (0x26 shl 24), alpha, Shader.TileMode.CLAMP)
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = heightPx / 18f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        return bmp
    }
}
