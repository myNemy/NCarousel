package dev.nemeyes.ncarousel.ui.snake

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.random.Random

private const val GRID_W = 20
private const val GRID_H = 20
private const val TICK_MS = 120L

private fun enqueueTurn(
    pending: SnapshotStateList<Dir>,
    currentDir: Dir,
    d: Dir,
) {
    val ref = pending.lastOrNull() ?: currentDir
    if (ref.isOpposite(d)) return
    if (pending.isNotEmpty() && pending.last() == d) return
    if (pending.size >= 2) {
        pending.removeAt(pending.lastIndex)
    }
    pending.add(d)
}

private data class Cell(val x: Int, val y: Int)

private enum class Dir { Up, Down, Left, Right }

private fun Dir.toVec(): Pair<Int, Int> =
    when (this) {
        Dir.Up -> 0 to -1
        Dir.Down -> 0 to 1
        Dir.Left -> -1 to 0
        Dir.Right -> 1 to 0
    }

private fun Dir.isOpposite(other: Dir): Boolean =
    when (this) {
        Dir.Up -> other == Dir.Down
        Dir.Down -> other == Dir.Up
        Dir.Left -> other == Dir.Right
        Dir.Right -> other == Dir.Left
    }

private data class SnakeState(
    val snake: List<Cell>,
    val dir: Dir,
    val food: Cell,
    val gameOver: Boolean,
    val score: Int,
)

private fun Int.floorMod(mod: Int): Int {
    val r = this % mod
    return if (r < 0) r + mod else r
}

private fun randomFood(w: Int, h: Int, occupied: Set<Cell>): Cell {
    val free = buildList {
        for (x in 0 until w) {
            for (y in 0 until h) {
                val c = Cell(x, y)
                if (c !in occupied) add(c)
            }
        }
    }
    require(free.isNotEmpty()) { "no empty cell for food" }
    return free.random(Random)
}

private fun initialState(): SnakeState {
    val head = Cell(GRID_W / 2, GRID_H / 2)
    val snake =
        listOf(
            head,
            Cell(head.x - 1, head.y),
            Cell(head.x - 2, head.y),
        )
    val occupied = snake.toSet()
    return SnakeState(
        snake = snake,
        dir = Dir.Right,
        food = randomFood(GRID_W, GRID_H, occupied),
        gameOver = false,
        score = 0,
    )
}

private fun step(state: SnakeState, input: Dir): SnakeState {
    if (state.gameOver) return state
    val dir = if (state.dir.isOpposite(input)) state.dir else input
    val (dx, dy) = dir.toVec()
    val head = state.snake.first()
    val next =
        Cell(
            x = (head.x + dx).floorMod(GRID_W),
            y = (head.y + dy).floorMod(GRID_H),
        )
    if (next in state.snake) {
        return state.copy(gameOver = true)
    }
    val eating = next == state.food
    val newSnake =
        if (eating) {
            listOf(next) + state.snake
        } else {
            listOf(next) + state.snake.dropLast(1)
        }
    val newFood =
        if (eating) {
            randomFood(GRID_W, GRID_H, newSnake.toSet())
        } else {
            state.food
        }
    return SnakeState(
        snake = newSnake,
        dir = dir,
        food = newFood,
        gameOver = false,
        score = if (eating) state.score + 1 else state.score,
    )
}

@Composable
fun NCSnakeScreen(modifier: Modifier = Modifier) {
    val state = remember { mutableStateOf(initialState()) }
    val pendingTurns = remember { mutableStateListOf<Dir>() }
    var soundOn by remember { mutableStateOf(true) }
    var lastDragDir by remember { mutableStateOf<Dir?>(null) }

    val toneGen =
        remember {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        }
    DisposableEffect(Unit) {
        onDispose { toneGen.release() }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(TICK_MS)
            val s = state.value
            if (s.gameOver) continue
            val input =
                if (pendingTurns.isNotEmpty()) {
                    pendingTurns.removeAt(0)
                } else {
                    s.dir
                }
            val next = step(s, input)
            if (soundOn) {
                when {
                    next.gameOver && !s.gameOver ->
                        toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 120)
                    next.score > s.score ->
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                }
            }
            state.value = next
        }
    }

    val s = state.value
    val lcdBg = Color(0xFF0A240A)
    val lcdDim = Color(0xFF143214)
    val snakeColor = Color(0xFF6BCB4F)
    val foodColor = Color(0xFF9AE66E)

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nc_snake_score, s.score),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.nc_snake_bip),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Switch(
                    checked = soundOn,
                    onCheckedChange = { soundOn = it },
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { lastDragDir = null },
                                onDragCancel = { lastDragDir = null },
                                onDragEnd = { lastDragDir = null },
                            ) { change, dragAmount ->
                                change.consume()
                                if (state.value.gameOver) return@detectDragGestures
                                val dx = dragAmount.x
                                val dy = dragAmount.y
                                val absX = abs(dx)
                                val absY = abs(dy)
                                val thresholdPx = 10f
                                if (absX < thresholdPx && absY < thresholdPx) return@detectDragGestures

                                val d =
                                    if (absX > absY) {
                                        if (dx > 0) Dir.Right else Dir.Left
                                    } else {
                                        if (dy > 0) Dir.Down else Dir.Up
                                    }
                                if (lastDragDir != d) {
                                    enqueueTurn(pendingTurns, state.value.dir, d)
                                    lastDragDir = d
                                }
                            }
                        },
            ) {
                val marginBg = Color(0xFF061806)
                drawRect(marginBg, size = size)
                val cell = minOf(size.width / GRID_W, size.height / GRID_H)
                val boardW = cell * GRID_W
                val boardH = cell * GRID_H
                val ox = (size.width - boardW) / 2f
                val oy = (size.height - boardH) / 2f
                drawRect(
                    lcdBg,
                    topLeft = Offset(ox, oy),
                    size = Size(boardW, boardH),
                )
                for (x in 0..GRID_W) {
                    val px = ox + x * cell
                    drawLine(
                        lcdDim,
                        Offset(px, oy),
                        Offset(px, oy + boardH),
                        strokeWidth = 1f,
                    )
                }
                for (y in 0..GRID_H) {
                    val py = oy + y * cell
                    drawLine(
                        lcdDim,
                        Offset(ox, py),
                        Offset(ox + boardW, py),
                        strokeWidth = 1f,
                    )
                }
                val inset = cell * 0.12f
                val inner = (cell - inset * 2f).coerceAtLeast(1f)
                for (c in s.snake) {
                    drawRect(
                        snakeColor,
                        topLeft =
                            Offset(
                                ox + c.x * cell + inset,
                                oy + c.y * cell + inset,
                            ),
                        size = Size(inner, inner),
                    )
                }
                val f = s.food
                drawRect(
                    foodColor,
                    topLeft =
                        Offset(
                            ox + f.x * cell + inset,
                            oy + f.y * cell + inset,
                        ),
                    size = Size(inner, inner),
                )
            }
        }
        if (s.gameOver) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.nc_snake_game_over),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    state.value = initialState()
                    pendingTurns.clear()
                },
            ) {
                Text(stringResource(R.string.nc_snake_restart))
            }
        }
        Spacer(Modifier.height(8.dp))
        DPad(
            enabled = !s.gameOver,
            onDir = { d ->
                enqueueTurn(pendingTurns, state.value.dir, d)
            },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DPad(
    enabled: Boolean,
    onDir: (Dir) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DPadButton(
            enabled = enabled,
            onPress = { onDir(Dir.Up) },
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.nc_snake_cd_up))
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DPadButton(
                enabled = enabled,
                onPress = { onDir(Dir.Left) },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nc_snake_cd_left))
            }
            Spacer(Modifier.width(52.dp))
            DPadButton(
                enabled = enabled,
                onPress = { onDir(Dir.Right) },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.nc_snake_cd_right))
            }
        }
        DPadButton(
            enabled = enabled,
            onPress = { onDir(Dir.Down) },
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.nc_snake_cd_down))
        }
    }
}

@Composable
private fun DPadButton(
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Use a raw press-down handler: IconButton/clickable typically fires on release.
    Surface(
        modifier =
            modifier.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                }
            },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
