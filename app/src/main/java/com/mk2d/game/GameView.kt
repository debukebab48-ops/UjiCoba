package com.mk2d.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context) : SurfaceView(context), Runnable {

    private var gameThread: Thread? = null
    @Volatile private var isPlaying = false
    private val surfaceHolder: SurfaceHolder = holder
    private val paint = Paint()

    // Posisi & Ukuran Karakter (Sementara pakai placeholder sebelum aset foto masuk)
    private var playerX = 300f
    private var playerY = 0f
    private val playerWidth = 140f
    private val playerHeight = 280f
    private val moveSpeed = 12f

    // Status Input Tombol
    private var moveLeft = false
    private var moveRight = false

    // Area Tombol Virtual di Layar
    private val btnLeftRect = RectF()
    private val btnRightRect = RectF()

    override fun run() {
        while (isPlaying) {
            update()
            draw()
            // Target ~60 FPS
            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun update() {
        if (width == 0 || height == 0) return

        // Set posisi lantai (85% dari tinggi layar)
        val groundY = height * 0.85f
        playerY = groundY - playerHeight

        // Gerak Kiri / Kanan + Batas Layar
        if (moveLeft) {
            playerX -= moveSpeed
            if (playerX < 50f) playerX = 50f
        }
        if (moveRight) {
            playerX += moveSpeed
            if (playerX > width - playerWidth - 50f) {
                playerX = width - playerWidth - 50f
            }
        }
    }

    private fun draw() {
        if (!surfaceHolder.surface.isValid) return
        val canvas: Canvas = surfaceHolder.lockCanvas()

        // 1. Background Stage Sementara
        canvas.drawColor(Color.parseColor("#1E1E24"))

        // 2. Lantai Arena
        val groundY = height * 0.85f
        paint.color = Color.parseColor("#3A3A4A")
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)

        // 3. Karakter (Placeholder Kotak Merah - siap diganti Bitmap PNG di Sesi 2)
        paint.color = Color.parseColor("#E63946")
        canvas.drawRect(playerX, playerY, playerX + playerWidth, playerY + playerHeight, paint)

        // 4. Tombol Kontrol Kiri & Kanan
        val btnSize = 150f
        val margin = 60f
        btnLeftRect.set(margin, height - btnSize - margin, margin + btnSize, height - margin)
        btnRightRect.set(margin * 2 + btnSize, height - btnSize - margin, margin * 2 + btnSize * 2, height - margin)

        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(btnLeftRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnRightRect, 20f, 20f, paint)

        // Label Teks Tombol
        paint.color = Color.BLACK
        paint.textSize = 50f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("<", btnLeftRect.centerX(), btnLeftRect.centerY() + 18f, paint)
        canvas.drawText(">", btnRightRect.centerX(), btnRightRect.centerY() + 18f, paint)

        surfaceHolder.unlockCanvasAndPost(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Reset input dulu, lalu cek semua jari yang menyentuh layar (Multi-touch ready)
        moveLeft = false
        moveRight = false

        val pointerCount = event.pointerCount
        for (i in 0 until pointerCount) {
            val action = event.actionMasked
            // Abaikan jari yang baru saja diangkat
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == event.actionIndex) {
                continue
            }

            val x = event.getX(i)
            val y = event.getY(i)

            if (btnLeftRect.contains(x, y)) moveLeft = true
            if (btnRightRect.contains(x, y)) moveRight = true
        }
        return true
    }

    fun resume() {
        isPlaying = true
        gameThread = Thread(this)
        gameThread?.start()
    }

    fun pause() {
        isPlaying = false
        try {
            gameThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
