package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val GravityFraction = 0.6f

/** Max horizontal drift of a top-rain piece, as a fraction of canvas width per second. */
private const val HorizontalDriftMax = 0.15f

/** Start fading the pieces once the burst has played this fraction of its duration. */
private const val FadeStartFraction = 0.65f

/** Where a burst's pieces originate. */
enum class ConfettiOrigin {
  /** Pieces rain from the top edge of the canvas. */
  TopRain,

  /** Pieces explode outward from [ConfettiBurst.burstPoint]. */
  CenterBurst,
}

private data class ConfettiPiece(
  val startX: Float,
  val startY: Float,
  val vx: Float,
  val vy: Float,
  val rotation0: Float,
  val spin: Float,
  val widthAspect: Float,
  val colorIndex: Int,
)

/**
 * A short-lived, self-clearing confetti burst drawn on a [Canvas]. Positions
 * are expressed as fractions of the canvas so the effect scales with its size;
 * colors come from the caller (phase theme containers, celebration accents).
 * The burst replays whenever [retriggerKey] changes and leaves nothing behind.
 */
@Composable
fun ConfettiBurst(
  colors: List<Color>,
  modifier: Modifier = Modifier,
  origin: ConfettiOrigin = ConfettiOrigin.CenterBurst,
  burstPoint: Offset = Offset(0.5f, 0.5f),
  intensity: Int = 36,
  durationMs: Int = 800,
  retriggerKey: Any = Unit,
) {
  require(colors.isNotEmpty()) { "ConfettiBurst needs at least one color" }
  val pieces = remember(origin, burstPoint, intensity, retriggerKey) {
    spawn(origin, burstPoint, intensity, colors.size)
  }
  var progress by remember(retriggerKey) { mutableFloatStateOf(0f) }

  LaunchedEffect(retriggerKey) {
    val start = withFrameNanos { it }
    while (true) {
      val frame = withFrameNanos { it }
      val elapsedMs = (frame - start) / 1_000_000f
      if (elapsedMs >= durationMs) break
      progress = elapsedMs / durationMs
    }
    progress = 0f
  }

  Canvas(modifier) {
    if (progress <= 0f || progress >= 1f) return@Canvas
    val time = progress * (durationMs / 1000f)
    val alpha = if (progress < FadeStartFraction) {
      1f
    } else {
      ((1f - progress) / (1f - FadeStartFraction)).coerceIn(0f, 1f)
    }
    val pieceWidth = Dimens.ConfettiPieceSize.toPx() * 2f
    val pieceHeight = Dimens.ConfettiPieceSize.toPx() * 0.6f
    pieces.forEach { piece ->
      val x = piece.startX * size.width + piece.vx * size.width * time
      val y = piece.startY * size.height + piece.vy * size.height * time +
        0.5f * GravityFraction * size.height * time * time
      if (y - pieceHeight > size.height) return@forEach
      rotate(
        degrees = piece.rotation0 + piece.spin * time,
        pivot = Offset(x, y),
      ) {
        drawRect(
          color = colors[piece.colorIndex].copy(alpha = alpha),
          topLeft = Offset(
            x - piece.widthAspect * pieceWidth / 2f,
            y - pieceHeight / 2f,
          ),
          size = Size(piece.widthAspect * pieceWidth, pieceHeight),
        )
      }
    }
  }
}

private fun spawn(
  origin: ConfettiOrigin,
  burstPoint: Offset,
  intensity: Int,
  colorCount: Int,
): List<ConfettiPiece> {
  val rnd = Random.Default
  return List(intensity) {
    val vx: Float
    val vy: Float
    var startX: Float
    var startY: Float
    if (origin == ConfettiOrigin.TopRain) {
      startX = rnd.nextFloat()
      startY = -(rnd.nextFloat() * 0.2f)
      vx = (rnd.nextFloat() - 0.5f) * 2f * HorizontalDriftMax
      vy = 0.5f + rnd.nextFloat() * 0.7f
    } else {
      startX = burstPoint.x + (rnd.nextFloat() - 0.5f) * 0.03f
      startY = burstPoint.y + (rnd.nextFloat() - 0.5f) * 0.03f
      val angle = rnd.nextFloat() * 2f * PI.toFloat()
      val speed = 0.25f + rnd.nextFloat() * 0.6f
      vx = cos(angle) * speed
      vy = sin(angle) * speed * 0.7f - 0.1f
    }
    ConfettiPiece(
      startX = startX,
      startY = startY,
      vx = vx,
      vy = vy,
      rotation0 = rnd.nextFloat() * 360f,
      spin = (rnd.nextFloat() - 0.5f) * 720f,
      widthAspect = 0.8f + rnd.nextFloat() * 0.8f,
      colorIndex = rnd.nextInt(colorCount),
    )
  }
}