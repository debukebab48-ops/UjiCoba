package com.mk2d.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF

class SpriteAnimation(
    private val frames: List<Bitmap>,
    private val frameDurationMs: Long = 120L,
    private val loop: Boolean = true
) {
    private var currentFrameIndex = 0
    private var lastFrameTime = System.currentTimeMillis()
    private val flipMatrix = Matrix()
    var isFinished = false
        private set

    val hasFrames: Boolean
        get() = frames.isNotEmpty()

    fun reset() {
        currentFrameIndex = 0
        lastFrameTime = System.currentTimeMillis()
        isFinished = false
    }

    fun update() {
        if (frames.isEmpty() || isFinished) return
        if (frames.size == 1 && loop) return

        val now = System.currentTimeMillis()
        if (now - lastFrameTime >= frameDurationMs) {
            lastFrameTime = now
            if (currentFrameIndex + 1 < frames.size) {
                currentFrameIndex++
            } else if (loop) {
                currentFrameIndex = 0
            } else {
                isFinished = true
            }
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
            flipMatrix.postScale(-scaleX, scaleY)
            flipMatrix.postTranslate(destRect.right, destRect.top)
        }

        canvas.drawBitmap(frame, flipMatrix, null)
    }
}
