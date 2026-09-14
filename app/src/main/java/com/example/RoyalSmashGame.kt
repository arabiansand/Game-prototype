package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Game constants
const val GRAVITY = 980f // px per second squared
const val RESTITUTION = 0.6f // Bounciness
const val BLOCK_WIDTH = 60f
const val BLOCK_HEIGHT = 60f
const val CANNON_RADIUS = 50f
const val BALL_RADIUS = 20f
const val MAX_AMMO = 5
const val CANNON_POWER = 1200f // Velocity multiplier

enum class GameState {
    PLAYING, WON, LOST
}

data class Block(var x: Float, var y: Float, val color: Color, var isDestroyed: Boolean = false)

data class Cannonball(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var active: Boolean = true
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val color: Color
)

class GameEngine {
    var gameState by mutableStateOf(GameState.PLAYING)
    var blocks = mutableStateOf<List<Block>>(emptyList())
    var cannonballs = mutableStateOf<List<Cannonball>>(emptyList())
    var particles = mutableStateOf<List<Particle>>(emptyList())
    var ammoCount by mutableStateOf(MAX_AMMO)
    
    var cannonAngle by mutableStateOf(-Math.PI.toFloat() / 2f) // Pointing straight up initially
    var isAiming by mutableStateOf(false)
    var aimStart = Offset.Zero
    var aimEnd = Offset.Zero

    var screenWidth = 0f
    var screenHeight = 0f

    var currentLevel by mutableStateOf(1)
    val maxLevel = 3

    var soundManager: GameSoundManager? = null

    fun initGame(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        loadLevel(currentLevel)
    }

    fun loadLevel(level: Int) {
        ammoCount = MAX_AMMO
        gameState = GameState.PLAYING
        cannonballs.value = emptyList()
        particles.value = emptyList()

        val newBlocks = mutableListOf<Block>()
        val colors = listOf(Color.Red, Color(0xFFFFA500), Color.Yellow, Color.Green, Color.Blue)

        when (level) {
            1 -> {
                // Level 1: Pyramid
                val startY = screenHeight * 0.4f
                val rows = 5
                for (row in 0 until rows) {
                    val numBlocks = row + 1
                    val startX = screenWidth / 2f - (numBlocks * BLOCK_WIDTH) / 2f
                    for (col in 0 until numBlocks) {
                        newBlocks.add(
                            Block(
                                x = startX + col * BLOCK_WIDTH,
                                y = startY + row * BLOCK_HEIGHT,
                                color = colors[row]
                            )
                        )
                    }
                }
            }
            2 -> {
                // Level 2: Wall (4 rows, 6 cols)
                val startY = screenHeight * 0.3f
                val rows = 4
                val cols = 6
                val startX = screenWidth / 2f - (cols * BLOCK_WIDTH) / 2f
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        newBlocks.add(
                            Block(
                                x = startX + col * BLOCK_WIDTH,
                                y = startY + row * BLOCK_HEIGHT,
                                color = colors[row % colors.size]
                            )
                        )
                    }
                }
            }
            3 -> {
                // Level 3: Two Towers
                val startY = screenHeight * 0.4f
                val rows = 6
                val towerGap = BLOCK_WIDTH * 2
                val leftStartX = screenWidth / 2f - towerGap / 2 - BLOCK_WIDTH
                val rightStartX = screenWidth / 2f + towerGap / 2

                for (row in 0 until rows) {
                    // Left Tower
                    newBlocks.add(Block(x = leftStartX, y = startY + row * BLOCK_HEIGHT, color = colors[row % colors.size]))
                    // Right Tower
                    newBlocks.add(Block(x = rightStartX, y = startY + row * BLOCK_HEIGHT, color = colors[row % colors.size]))
                }
                
                // Bridge on top
                newBlocks.add(Block(x = screenWidth / 2f - BLOCK_WIDTH / 2, y = startY - BLOCK_HEIGHT, color = Color.Magenta))
                newBlocks.add(Block(x = leftStartX, y = startY - BLOCK_HEIGHT, color = Color.Magenta))
                newBlocks.add(Block(x = rightStartX, y = startY - BLOCK_HEIGHT, color = Color.Magenta))
            }
            else -> {
                // End of game loop / fallback
            }
        }
        blocks.value = newBlocks
    }

    fun spawnParticles(x: Float, y: Float, color: Color) {
        val newParticles = mutableListOf<Particle>()
        for (i in 0 until 20) {
            val angle = Math.random() * 2 * Math.PI
            val speed = Math.random() * 400f + 100f
            val life = (Math.random() * 0.3f + 0.2f).toFloat() // 0.2s to 0.5s
            newParticles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    life = life,
                    maxLife = life,
                    color = color
                )
            )
        }
        particles.value = particles.value + newParticles
    }

    fun update(dt: Float) {
        if (gameState != GameState.PLAYING) return

        val activeBalls = cannonballs.value.filter { it.active }.toMutableList()
        val currentBlocks = blocks.value.toMutableList()
        val currentParticles = particles.value.toMutableList()
        
        var blocksChanged = false

        // Update particles
        val aliveParticles = mutableListOf<Particle>()
        for (p in currentParticles) {
            p.life -= dt
            if (p.life > 0) {
                p.vy += GRAVITY * dt * 0.5f // Less gravity for particles
                p.x += p.vx * dt
                p.y += p.vy * dt
                aliveParticles.add(p)
            }
        }
        particles.value = aliveParticles

        for (ball in activeBalls) {
            // Apply gravity
            ball.vy += GRAVITY * dt

            // Update position
            ball.x += ball.vx * dt
            ball.y += ball.vy * dt

            // Screen bounds collision
            if (ball.x - BALL_RADIUS < 0) {
                ball.x = BALL_RADIUS
                ball.vx *= -RESTITUTION
            } else if (ball.x + BALL_RADIUS > screenWidth) {
                ball.x = screenWidth - BALL_RADIUS
                ball.vx *= -RESTITUTION
            }

            // Ground collision - ball is destroyed when it hits the ground
            if (ball.y > screenHeight) {
                ball.active = false
            }

            // Block collision (AABB vs Circle simplified)
            for (block in currentBlocks) {
                if (block.isDestroyed) continue

                // Closest point on the AABB to the circle center
                val closestX = ball.x.coerceIn(block.x, block.x + BLOCK_WIDTH)
                val closestY = ball.y.coerceIn(block.y, block.y + BLOCK_HEIGHT)

                val dx = ball.x - closestX
                val dy = ball.y - closestY
                val distanceSquared = dx * dx + dy * dy

                if (distanceSquared < BALL_RADIUS * BALL_RADIUS) {
                    // Collision occurred!
                    block.isDestroyed = true
                    blocksChanged = true
                    soundManager?.playHit()
                    spawnParticles(block.x + BLOCK_WIDTH / 2f, block.y + BLOCK_HEIGHT / 2f, block.color)

                    // Determine bounce direction
                    if (ball.x < block.x || ball.x > block.x + BLOCK_WIDTH) {
                        ball.vx *= -RESTITUTION
                    } else {
                        ball.vy *= -RESTITUTION
                    }
                }
            }
        }

        cannonballs.value = activeBalls
        if (blocksChanged) {
            blocks.value = currentBlocks
            
            // Check win condition
            if (currentBlocks.none { !it.isDestroyed }) {
                if (gameState != GameState.WON) {
                    gameState = GameState.WON
                    soundManager?.playLevelClear()
                }
            }
        } else if (ammoCount == 0 && activeBalls.isEmpty() && currentBlocks.any { !it.isDestroyed }) {
            // Check lose condition
            gameState = GameState.LOST
        }
    }

    fun fire() {
        if (ammoCount > 0 && gameState == GameState.PLAYING) {
            ammoCount--
            
            // Calculate velocity from angle and a fixed power
            val vx = cos(cannonAngle) * CANNON_POWER
            val vy = sin(cannonAngle) * CANNON_POWER
            
            val newBall = Cannonball(
                x = screenWidth / 2f,
                y = screenHeight - CANNON_RADIUS,
                vx = vx,
                vy = vy
            )
            cannonballs.value = cannonballs.value + newBall
            soundManager?.playShoot()
        }
    }
}

@Composable
fun RoyalSmashGame() {
    val soundManager = remember { GameSoundManager() }
    val engine = remember { GameEngine().apply { this.soundManager = soundManager } }
    var lastFrameTime by remember { mutableStateOf(0L) }
    
    val bgImage = ImageBitmap.imageResource(id = R.drawable.img_game_bg)
    val brickImage = ImageBitmap.imageResource(id = R.drawable.img_brick)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameTime ->
                if (lastFrameTime == 0L) lastFrameTime = frameTime
                val dt = (frameTime - lastFrameTime) / 1000f
                lastFrameTime = frameTime
                
                if (dt < 0.1f) { // Prevent huge jumps if suspended
                    engine.update(dt)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val anyPressed = event.changes.any { it.pressed }

                            val cannonX = size.width / 2f
                            val cannonY = size.height - CANNON_RADIUS / 2f

                            if (anyPressed) {
                                if (engine.gameState == GameState.PLAYING) {
                                    engine.isAiming = true
                                    // Point cannon directly at the touch position
                                    engine.cannonAngle = kotlin.math.atan2(change.position.y - cannonY, change.position.x - cannonX)
                                }
                            } else {
                                if (engine.isAiming) {
                                    engine.isAiming = false
                                    engine.fire()
                                }
                            }
                        }
                    }
                }
        ) {
            if (engine.screenWidth == 0f) {
                engine.initGame(size.width, size.height)
            }

            // Draw Background (4K texture)
            drawImage(
                image = bgImage,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )

            // Draw Blocks
            // Calculate Pedestal Position
            val maxBlockY = engine.blocks.value.maxOfOrNull { it.y + BLOCK_HEIGHT }
            val pedestalY = if (maxBlockY != null) maxBlockY + 10f else size.height * 0.6f
            val pedestalX = size.width / 2f
            
            // Draw Pedestal Pillar
            drawRect(
                color = Color(0xFFFFC107),
                topLeft = Offset(pedestalX - 30f, pedestalY),
                size = Size(60f, size.height - pedestalY)
            )
            // Pillar accents
            drawRect(color = Color(0xFF9C27B0), topLeft = Offset(pedestalX - 30f, pedestalY + 20f), size = Size(60f, 20f))
            drawRect(color = Color(0xFF9C27B0), topLeft = Offset(pedestalX - 30f, pedestalY + 80f), size = Size(60f, 20f))
            
            // Top Plate
            drawOval(
                color = Color(0xFF9C27B0),
                topLeft = Offset(pedestalX - 200f, pedestalY - 25f),
                size = Size(400f, 50f)
            )
            drawOval(
                color = Color(0xFFFFC107),
                topLeft = Offset(pedestalX - 190f, pedestalY - 20f),
                size = Size(380f, 40f)
            )

            for (block in engine.blocks.value) {
                if (!block.isDestroyed) {
                    // Draw texture
                    drawImage(
                        image = brickImage,
                        dstOffset = IntOffset(block.x.toInt(), block.y.toInt()),
                        dstSize = IntSize(BLOCK_WIDTH.toInt(), BLOCK_HEIGHT.toInt())
                    )
                    // Tint it with the block's color
                    drawRect(
                        color = block.color.copy(alpha = 0.4f),
                        topLeft = Offset(block.x, block.y),
                        size = Size(BLOCK_WIDTH, BLOCK_HEIGHT)
                    )
                    // Block border
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(block.x, block.y),
                        size = Size(BLOCK_WIDTH, BLOCK_HEIGHT),
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Draw Cannonballs
            for (ball in engine.cannonballs.value) {
                if (ball.active) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Yellow, Color.Red),
                            center = Offset(ball.x - BALL_RADIUS / 4, ball.y - BALL_RADIUS / 4),
                            radius = BALL_RADIUS * 1.5f
                        ),
                        radius = BALL_RADIUS,
                        center = Offset(ball.x, ball.y)
                    )
                }
            }

            // Draw Particles
            for (p in engine.particles.value) {
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = 4f + (alpha * 4f), // Shrink as they die
                    center = Offset(p.x, p.y)
                )
            }

            // Draw Cannon Base (Concentric Rings)
            val ringCenter = Offset(size.width / 2f, size.height + 20f)
            drawArc(color = Color(0xFF673AB7), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(ringCenter.x - 180f, ringCenter.y - 180f), size = Size(360f, 360f))
            drawArc(color = Color(0xFFFFC107), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(ringCenter.x - 150f, ringCenter.y - 150f), size = Size(300f, 300f))
            drawArc(color = Color(0xFF9C27B0), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(ringCenter.x - 110f, ringCenter.y - 110f), size = Size(220f, 220f))

            // Cannon Barrel
            val barrelLength = CANNON_RADIUS * 1.5f
            val barrelEndX = ringCenter.x + cos(engine.cannonAngle) * barrelLength
            val barrelEndY = ringCenter.y + sin(engine.cannonAngle) * barrelLength
            
            withTransform({
                translate(ringCenter.x, ringCenter.y)
                rotate(degrees = (engine.cannonAngle * 180 / Math.PI).toFloat())
            }) {
                // Red barrel
                drawRoundRect(
                    color = Color(0xFFE53935),
                    topLeft = Offset(0f, -40f),
                    size = Size(140f, 80f),
                    cornerRadius = CornerRadius(20f, 20f)
                )
                // Gold bands
                drawRect(color = Color(0xFFFFD700), topLeft = Offset(30f, -40f), size = Size(15f, 80f))
                drawRect(color = Color(0xFFFFD700), topLeft = Offset(110f, -45f), size = Size(25f, 90f))
                // Blue nozzle
                drawRoundRect(
                    color = Color(0xFF1E88E5),
                    topLeft = Offset(135f, -50f),
                    size = Size(30f, 100f),
                    cornerRadius = CornerRadius(15f, 15f)
                )
            }

            // Cannon dome (center pivot)
            drawCircle(color = Color(0xFFE53935), radius = 60f, center = ringCenter)
            drawCircle(color = Color(0xFF1E88E5), radius = 30f, center = ringCenter)
            drawCircle(color = Color(0xFFFFD700), radius = 15f, center = ringCenter)

            // Draw Trajectory (Aiming)
            if (engine.isAiming && engine.ammoCount > 0) {
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawLine(
                    color = Color.White,
                    start = Offset(barrelEndX, barrelEndY),
                    end = Offset(
                        barrelEndX + cos(engine.cannonAngle) * 300f,
                        barrelEndY + sin(engine.cannonAngle) * 300f
                    ),
                    strokeWidth = 5f,
                    pathEffect = pathEffect
                )
            }

            // Draw UI (Ammo indicator base)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Yellow, Color.Red),
                    center = Offset(45f, 45f),
                    radius = 30f
                ),
                radius = 20f,
                center = Offset(50f, 50f)
            )
        }

        // Ammo Text
        Text(
            text = "x ${engine.ammoCount}",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 4f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 80.dp, top = 30.dp)
        )

        // Top Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFFB71C1C), Color(0xFFE53935))),
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .border(
                    width = 4.dp,
                    color = Color(0xFFFFD700),
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val bannerText = when (engine.currentLevel) {
                1 -> "AIM, FIRE\nDESTROY!"
                2 -> "ONE SHOT\nMAX DAMAGE"
                3 -> "TOO HARD\nFOR YOU?"
                else -> "AIM, FIRE\nDESTROY!"
            }
            Text(
                text = bannerText,
                color = Color(0xFFFFD700),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 4f)
                )
            )
        }

        if (engine.gameState == GameState.WON) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (engine.currentLevel < engine.maxLevel) "LEVEL CLEARED!" else "YOU BEAT THE GAME!",
                    color = Color.Green,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (engine.currentLevel < engine.maxLevel) {
                    Button(onClick = {
                        engine.currentLevel++
                        engine.loadLevel(engine.currentLevel)
                    }) {
                        Text("Next Level")
                    }
                } else {
                    Button(onClick = {
                        engine.currentLevel = 1
                        engine.loadLevel(engine.currentLevel)
                    }) {
                        Text("Play Again")
                    }
                }
            }
        } else if (engine.gameState == GameState.LOST) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GAME OVER",
                    color = Color.Red,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    engine.loadLevel(engine.currentLevel)
                }) {
                    Text("Retry Level")
                }
            }
        }
    }
}
