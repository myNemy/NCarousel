package dev.nemeyes.ncarousel.ui.snake

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

private const val GRID_W = 12
private const val GRID_H = 20
private const val TICK_MS = 175L

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
    val queuedDir = remember { mutableStateOf<Dir?>(null) }
    var soundOn by remember { mutableStateOf(true) }

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
            val input = queuedDir.value ?: s.dir
            queuedDir.value = null
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
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            val cell =
                minOf(
                    maxW / GRID_W,
                    maxH / GRID_H,
                )
            val boardW = cell * GRID_W
            val boardH = cell * GRID_H
            Canvas(
                modifier =
                    Modifier
                        .width(boardW)
                        .height(boardH)
                        .align(Alignment.TopCenter),
            ) {
                drawRect(lcdBg, size = size)
                for (x in 0..GRID_W) {
                    val px = x * cell.toPx()
                    drawLine(
                        lcdDim,
                        Offset(px, 0f),
                        Offset(px, size.height),
                        strokeWidth = 1f,
                    )
                }
                for (y in 0..GRID_H) {
                    val py = y * cell.toPx()
                    drawLine(
                        lcdDim,
                        Offset(0f, py),
                        Offset(size.width, py),
                        strokeWidth = 1f,
                    )
                }
                val inset = cell.toPx() * 0.12f
                val inner = cell.toPx() - inset * 2
                for (c in s.snake) {
                    drawRect(
                        snakeColor,
                        topLeft =
                            Offset(
                                c.x * cell.toPx() + inset,
                                c.y * cell.toPx() + inset,
                            ),
                        size = Size(inner, inner),
                    )
                }
                val f = s.food
                drawRect(
                    foodColor,
                    topLeft =
                        Offset(
                            f.x * cell.toPx() + inset,
                            f.y * cell.toPx() + inset,
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
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    state.value = initialState()
                    queuedDir.value = null
                },
            ) {
                Text(stringResource(R.string.nc_snake_restart))
            }
        }
        Spacer(Modifier.height(12.dp))
        DPad(
            enabled = !s.gameOver,
            onDir = { d ->
                queuedDir.value = d
            },
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DPad(
    enabled: Boolean,
    onDir: (Dir) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = { onDir(Dir.Up) },
            enabled = enabled,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.nc_snake_cd_up))
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onDir(Dir.Left) },
                enabled = enabled,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nc_snake_cd_left))
            }
            Spacer(Modifier.width(52.dp))
            IconButton(
                onClick = { onDir(Dir.Right) },
                enabled = enabled,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.nc_snake_cd_right))
            }
        }
        IconButton(
            onClick = { onDir(Dir.Down) },
            enabled = enabled,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.nc_snake_cd_down))
        }
    }
}
