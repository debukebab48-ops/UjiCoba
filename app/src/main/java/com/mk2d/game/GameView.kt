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

    private enum class AnimState { IDLE, WALK }

    // Posisi & Ukuran Karakter
    private var playerX = 300f
    private var playerY = 0f
    private val playerWidth = 180f
    private val playerHeight = 320f
    private val moveSpeed = 12f
    private var facingRight = true
    private var currentState = AnimState.IDLE
    private val playerRect = RectF()

    // Aset Per-Frame & Stage
    private val idleAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_idle_"), frameDurationMs = 140L)
    }
    private val walkAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_walk_"), frameDurationMs = 90L)
    }
    private val stageBitmap: android.graphics.Bitmap? by lazy {
        loadDrawableBitmap("stage_bg")
    }

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

        // Gerak Kiri / Kanan + Arah Hadap + Batas Layar
        var isMoving = false
        if (moveLeft && !moveRight) {
            facingRight = false
            isMoving = true
            playerX -= moveSpeed
        } else if (moveRight && !moveLeft) {
            facingRight = true
            isMoving = true
            playerX += moveSpeed
        }

        if (playerX < 50f) playerX = 50f
        if (playerX > width - playerWidth - 50f) {
            playerX = width - playerWidth - 50f
        }

        val nextState = if (isMoving) AnimState.WALK else AnimState.IDLE
        if (nextState != currentState) {
            currentState = nextState
            if (currentState == AnimState.WALK) walkAnim.reset() else idleAnim.reset()
        }

        if (currentState == AnimState.WALK) {
            walkAnim.update()
        } else {
            idleAnim.update()
        }

        playerRect.set(playerX, playerY, playerX + playerWidth, playerY + playerHeight)
    }

    private fun loadDrawableBitmap(resName: String): android.graphics.Bitmap? {
        val resId = resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId == 0) return null
        return android.graphics.BitmapFactory.decodeResource(resources, resId)
    }

    private fun loadFrames(prefix: String): List<android.graphics.Bitmap> {
        val list = mutableListOf<android.graphics.Bitmap>()
        var index = 0
        while (true) {
            val bmp = loadDrawableBitmap("$prefix$index") ?: break
            list.add(bmp)
            index++
        }
        return list
    }

    private fun draw() {
        if (!surfaceHolder.surface.isValid) return
        val canvas: Canvas = surfaceHolder.lockCanvas()

        // 1. Background Stage (pakai stage_bg.png kalau ada, fallback warna polos)
        val bg = stageBitmap
        if (bg != null) {
            canvas.drawBitmap(bg, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        } else {
            canvas.drawColor(Color.parseColor("#1E1E24"))
        }

        // 2. Lantai Arena
        val groundY = height * 0.85f
        paint.color = Color.argb(190, 58, 58, 74)
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)

        // 3. Karakter Per-Frame (fallback kotak merah jika belum ada PNG di drawable)
        val activeAnim = if (currentState == AnimState.WALK) walkAnim else idleAnim
        if (activeAnim.hasFrames) {
            activeAnim.draw(canvas, playerRect, facingRight)
        } else {
            paint.color = Color.parseColor("#E63946")
            canvas.drawRect(playerRect, paint)
        }

        // Info Debug sementara di pojok kiri atas
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Idle: ${idleAnim.hasFrames} | Walk: ${walkAnim.hasFrames}", 40f, 60f, paint)

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
