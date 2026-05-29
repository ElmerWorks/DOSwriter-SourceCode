package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class ReferenceOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val imageMatrix = Matrix()
    private var scale = 1f
    private var panX = 0f
    private var panY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val bgPaint = Paint().apply { color = Color.BLACK }

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scale *= detector.scaleFactor
            scale = scale.coerceIn(0.1f, 10f)
            invalidate()
            return true
        }
    })

    fun show(assetPath: String) {
        try {
            context.assets.open(assetPath).use {
                bitmap = BitmapFactory.decodeStream(it)
            }
            scale = 1f
            panX = 0f
            panY = 0f
            visibility = VISIBLE
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        visibility = GONE
        bitmap = null
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && 
            (event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) || event.isFromSource(InputDevice.SOURCE_MOUSE))) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                scale *= (1f + vScroll * 0.1f)
                scale = scale.coerceIn(0.1f, 10f)
                invalidate()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        scaleGestureDetector.onTouchEvent(event)
        if (!scaleGestureDetector.isInProgress) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    panX += dx
                    panY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bitmap?.let { bmp ->
            imageMatrix.reset()
            val fitScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            imageMatrix.postScale(fitScale * scale, fitScale * scale)
            val dx = (width - bmp.width * fitScale * scale) / 2f + panX
            val dy = (height - bmp.height * fitScale * scale) / 2f + panY
            imageMatrix.postTranslate(dx, dy)
            canvas.drawBitmap(bmp, imageMatrix, null)
        }
    }
}
