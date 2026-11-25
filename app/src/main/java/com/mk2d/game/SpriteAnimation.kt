package com.mk2d.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF

class SpriteAnimation(
    private val frames: List<Bitmap>,
    private val frameDurationMs: Long = 120L
) {
    private var currentFrameIndex = 0
    private var lastFrameTime = System.currentTimeMillis()
    private val flipMatrix = Matrix()

    val hasFrames: Boolean
        get() = frames.isNotEmpty()

    fun reset() {
        currentFrameIndex = 0
        lastFrameTime = System.currentTimeMillis()
    }

    fun update() {
        if (frames.size <= 1) return

        val now = System.currentTimeMillis()
        if (now - lastFrameTime >= frameDurationMs) {
            currentFrameIndex = (currentFrameIndex + 1) % frames.size
            lastFrameTime = now
        }
    }

    fun draw(canvas: Canvas, destRect: RectF, facingRight: Boolean) {
        if (frames.isEmpty()) return

        val frame = frames[currentFrameIndex]
        val scaleX = destRect.width() / frame.width.toFloat()
        val scaleY = destRect.height() / frame.height.toFloat()

        flipMatrix.reset()
        if (facingRight) {
            flipMatrix.postScale(scaleX, scaleY)
            flipMatrix.postTranslate(destRect.left, destRect.top)
        } else {
            // Flip horizontal dari sumbu kanan lalu geser ke posisi destRect
            flipMatrix.postScale(-scaleX, scaleY)
            flipMatrix.postTranslate(destRect.right, destRect.top)
        }

        canvas.drawBitmap(frame, flipMatrix, null)
    }
}
