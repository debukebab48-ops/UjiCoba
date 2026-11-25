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

    private enum class AnimState { IDLE, WALK, PUNCH, KICK, HIT }

    // Posisi & Ukuran Karakter Player
    private var playerX = 260f
    private var playerY = 0f
    private val playerWidth = 180f
    private val playerHeight = 320f
    private val moveSpeed = 12f
    private var facingRight = true
    private var currentState = AnimState.IDLE
    private val playerRect = RectF()
    private var playerHp = 100
    private val maxHp = 100
    private var attackHasHit = false

    // Posisi & Status Musuh Dummy
    private var enemyX = 760f
    private var enemyY = 0f
    private val enemyWidth = 180f
    private val enemyHeight = 320f
    private var enemyHp = 100
    private val enemyRect = RectF()
    private var enemyHitFlashUntil = 0L

    // Aset Per-Frame & Stage
    private val idleAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_idle_"), frameDurationMs = 140L, loop = true)
    }
    private val walkAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_walk_"), frameDurationMs = 90L, loop = true)
    }
    private val punchAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_punch_"), frameDurationMs = 80L, loop = false)
    }
    private val kickAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_kick_"), frameDurationMs = 95L, loop = false)
    }
    private val hitAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("char_hit_"), frameDurationMs = 120L, loop = false)
    }
    private val enemyIdleAnim: SpriteAnimation by lazy {
        SpriteAnimation(loadFrames("enemy_idle_"), frameDurationMs = 150L, loop = true)
    }
    private val stageBitmap: android.graphics.Bitmap? by lazy {
        loadDrawableBitmap("stage_bg")
    }

    // Status Input Tombol
    private var moveLeft = false
    private var moveRight = false
    private var punchPressed = false
    private var kickPressed = false

    // Area Tombol Virtual di Layar
    private val btnLeftRect = RectF()
    private val btnRightRect = RectF()
    private val btnPunchRect = RectF()
    private val btnKickRect = RectF()

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

        // Posisikan musuh di sisi kanan saat pertama kali ukuran layar terbaca
        if (enemyX > width - enemyWidth - 60f) {
            enemyX = (width - enemyWidth - 120f).coerceAtLeast(playerX + 250f)
        }

        val groundY = height * 0.85f
        playerY = groundY - playerHeight
        enemyY = groundY - enemyHeight

        val isAttacking = currentState == AnimState.PUNCH || currentState == AnimState.KICK
        val isHurt = currentState == AnimState.HIT

        // Trigger serangan baru kalau tidak sedang terkena hit
        if (!isHurt) {
            if (punchPressed && currentState != AnimState.PUNCH) {
                punchPressed = false
                currentState = AnimState.PUNCH
                attackHasHit = false
                punchAnim.reset()
            } else if (kickPressed && currentState != AnimState.KICK) {
                kickPressed = false
                currentState = AnimState.KICK
                attackHasHit = false
                kickAnim.reset()
            }
        } else {
            punchPressed = false
            kickPressed = false
        }

        // Gerak kiri/kanan hanya saat tidak sedang memukul/menendang/kena hit
        if (!isAttacking && !isHurt) {
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

            val nextState = if (isMoving) AnimState.WALK else AnimState.IDLE
            if (nextState != currentState) {
                currentState = nextState
                if (currentState == AnimState.WALK) walkAnim.reset() else idleAnim.reset()
            }
        }

        // Update animasi sesuai state aktif
        when (currentState) {
            AnimState.IDLE -> idleAnim.update()
            AnimState.WALK -> walkAnim.update()
            AnimState.PUNCH -> {
                if (punchAnim.hasFrames) {
                    punchAnim.update()
                    if (punchAnim.isFinished) currentState = AnimState.IDLE
                } else {
                    // Fallback durasi jika belum ada PNG punch
                    currentState = AnimState.IDLE
                }
            }
            AnimState.KICK -> {
                if (kickAnim.hasFrames) {
                    kickAnim.update()
                    if (kickAnim.isFinished) currentState = AnimState.IDLE
                } else {
                    currentState = AnimState.IDLE
                }
            }
            AnimState.HIT -> {
                if (hitAnim.hasFrames) {
                    hitAnim.update()
                    if (hitAnim.isFinished) currentState = AnimState.IDLE
                } else {
                    currentState = AnimState.IDLE
                }
            }
        }

        // Cek hitbox serangan ke musuh (1 kali hit per 1 aksi serangan)
        if ((currentState == AnimState.PUNCH || currentState == AnimState.KICK) && !attackHasHit) {
            val reach = if (currentState == AnimState.KICK) 110f else 80f
            val damage = if (currentState == AnimState.KICK) 15 else 10
            val hitbox = if (facingRight) {
                RectF(playerX + playerWidth, playerY + 40f, playerX + playerWidth + reach, playerY + playerHeight - 40f)
            } else {
                RectF(playerX - reach, playerY + 40f, playerX, playerY + playerHeight - 40f)
            }

            enemyRect.set(enemyX, enemyY, enemyX + enemyWidth, enemyY + enemyHeight)
            if (RectF.intersects(hitbox, enemyRect) && enemyHp > 0) {
                attackHasHit = true
                enemyHp = (enemyHp - damage).coerceAtLeast(0)
                enemyHitFlashUntil = System.currentTimeMillis() + 140L
                val push = if (facingRight) 35f else -35f
                enemyX += push
            }
        }

        // Batas arena kiri-kanan
        if (playerX < 50f) playerX = 50f
        if (playerX > width - playerWidth - 50f) playerX = width - playerWidth - 50f
        if (enemyX < 50f) enemyX = 50f
        if (enemyX > width - enemyWidth - 50f) enemyX = width - enemyWidth - 50f

        enemyIdleAnim.update()
        playerRect.set(playerX, playerY, playerX + playerWidth, playerY + playerHeight)
        enemyRect.set(enemyX, enemyY, enemyX + enemyWidth, enemyY + enemyHeight)
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

        val bg = stageBitmap
        if (bg != null) {
            canvas.drawBitmap(bg, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        } else {
            canvas.drawColor(Color.parseColor("#1E1E24"))
        }

        val groundY = height * 0.85f
        paint.color = Color.argb(190, 58, 58, 74)
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)

        // Musuh Dummy
        val enemyFaceRight = enemyX < playerX
        val isEnemyFlashing = System.currentTimeMillis() < enemyHitFlashUntil
        if (enemyIdleAnim.hasFrames) {
            enemyIdleAnim.draw(canvas, enemyRect, enemyFaceRight)
            if (isEnemyFlashing) {
                paint.color = Color.argb(130, 255, 255, 255)
                canvas.drawRect(enemyRect, paint)
            }
        } else {
            paint.color = if (isEnemyFlashing) Color.WHITE else Color.parseColor("#457B9D")
            canvas.drawRect(enemyRect, paint)
        }

        // Player
        val activeAnim = when (currentState) {
            AnimState.WALK -> walkAnim
            AnimState.PUNCH -> punchAnim
            AnimState.KICK -> kickAnim
            AnimState.HIT -> hitAnim
            AnimState.IDLE -> idleAnim
        }
        if (activeAnim.hasFrames) {
            activeAnim.draw(canvas, playerRect, facingRight)
        } else {
            paint.color = Color.parseColor("#E63946")
            canvas.drawRect(playerRect, paint)
        }

        // HP Bar Player & Musuh
        val barWidth = width * 0.36f
        val barHeight = 28f
        val topMargin = 36f

        paint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(40f, topMargin, 40f + barWidth, topMargin + barHeight, paint)
        paint.color = Color.parseColor("#E63946")
        canvas.drawRect(40f, topMargin, 40f + barWidth * (playerHp / maxHp.toFloat()), topMargin + barHeight, paint)

        val enemyBarLeft = width - 40f - barWidth
        paint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(enemyBarLeft, topMargin, width - 40f, topMargin + barHeight, paint)
        paint.color = Color.parseColor("#FFB703")
        canvas.drawRect(enemyBarLeft, topMargin, width - 40f - barWidth * (1f - enemyHp / maxHp.toFloat()), topMargin + barHeight, paint)

        // Tombol < > di kiri bawah dan P K di kanan bawah
        val btnSize = 140f
        val margin = 50f
        btnLeftRect.set(margin, height - btnSize - margin, margin + btnSize, height - margin)
        btnRightRect.set(margin * 2 + btnSize, height - btnSize - margin, margin * 2 + btnSize * 2, height - margin)

        btnKickRect.set(width - margin - btnSize, height - btnSize - margin, width - margin, height - margin)
        btnPunchRect.set(width - margin * 2 - btnSize * 2, height - btnSize - margin, width - margin * 2 - btnSize, height - margin)

        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(btnLeftRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnRightRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnPunchRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnKickRect, 20f, 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("<", btnLeftRect.centerX(), btnLeftRect.centerY() + 16f, paint)
        canvas.drawText(">", btnRightRect.centerX(), btnRightRect.centerY() + 16f, paint)
        canvas.drawText("P", btnPunchRect.centerX(), btnPunchRect.centerY() + 16f, paint)
        canvas.drawText("K", btnKickRect.centerX(), btnKickRect.centerY() + 16f, paint)

        surfaceHolder.unlockCanvasAndPost(canvas)
    }

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

        // 3. Musuh Dummy (hadapi player)
        val enemyFaceRight = enemyX < playerX
        val isEnemyFlashing = System.currentTimeMillis() < enemyHitFlashUntil
        if (enemyIdleAnim.hasFrames) {
            enemyIdleAnim.draw(canvas, enemyRect, enemyFaceRight)
            if (isEnemyFlashing) {
                paint.color = Color.argb(130, 255, 255, 255)
                canvas.drawRect(enemyRect, paint)
            }
        } else {
            paint.color = if (isEnemyFlashing) Color.WHITE else Color.parseColor("#457B9D")
            canvas.drawRect(enemyRect, paint)
        }

        // 4. Karakter Player Per-Frame
        val activeAnim = when (currentState) {
            AnimState.WALK -> walkAnim
            AnimState.PUNCH -> punchAnim
            AnimState.KICK -> kickAnim
            AnimState.HIT -> hitAnim
            AnimState.IDLE -> idleAnim
        }
        if (activeAnim.hasFrames) {
            activeAnim.draw(canvas, playerRect, facingRight)
        } else {
            paint.color = Color.parseColor("#E63946")
            canvas.drawRect(playerRect, paint)
        }

        // 5. HP Bar Player (kiri atas) & Musuh (kanan atas)
        val barWidth = width * 0.36f
        val barHeight = 28f
        val topMargin = 36f

        paint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(40f, topMargin, 40f + barWidth, topMargin + barHeight, paint)
        paint.color = Color.parseColor("#E63946")
        canvas.drawRect(40f, topMargin, 40f + barWidth * (playerHp / maxHp.toFloat()), topMargin + barHeight, paint)

        val enemyBarLeft = width - 40f - barWidth
        paint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(enemyBarLeft, topMargin, width - 40f, topMargin + barHeight, paint)
        paint.color = Color.parseColor("#FFB703")
        canvas.drawRect(enemyBarLeft, topMargin, width - 40f - barWidth * (1f - enemyHp / maxHp.toFloat()), topMargin + barHeight, paint)

        // 6. Tombol Kontrol Kiri, Kanan (kiri bawah) & Pukul, Tendang (kanan bawah)
        val btnSize = 140f
        val margin = 50f
        btnLeftRect.set(margin, height - btnSize - margin, margin + btnSize, height - margin)
        btnRightRect.set(margin * 2 + btnSize, height - btnSize - margin, margin * 2 + btnSize * 2, height - margin)

        btnKickRect.set(width - margin - btnSize, height - btnSize - margin, width - margin, height - margin)
        btnPunchRect.set(width - margin * 2 - btnSize * 2, height - btnSize - margin, width - margin * 2 - btnSize, height - margin)

        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(btnLeftRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnRightRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnPunchRect, 20f, 20f, paint)
        canvas.drawRoundRect(btnKickRect, 20f, 20f, paint)

        // Label Teks Tombol
        paint.color = Color.BLACK
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("<", btnLeftRect.centerX(), btnLeftRect.centerY() + 16f, paint)
        canvas.drawText(">", btnRightRect.centerX(), btnRightRect.centerY() + 16f, paint)
        canvas.drawText("P", btnPunchRect.centerX(), btnPunchRect.centerY() + 16f, paint)
        canvas.drawText("K", btnKickRect.centerX(), btnKickRect.centerY() + 16f, paint)

        surfaceHolder.unlockCanvasAndPost(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        moveLeft = false
        moveRight = false

        val action = event.actionMasked
        val isDownEvent = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN
        val downIndex = if (isDownEvent) event.actionIndex else -1

        val pointerCount = event.pointerCount
        for (i in 0 until pointerCount) {
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == event.actionIndex) {
                continue
            }

            val x = event.getX(i)
            val y = event.getY(i)

            if (btnLeftRect.contains(x, y)) moveLeft = true
            if (btnRightRect.contains(x, y)) moveRight = true

            if (i == downIndex) {
                if (btnPunchRect.contains(x, y)) punchPressed = true
                if (btnKickRect.contains(x, y)) kickPressed = true
            }
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
