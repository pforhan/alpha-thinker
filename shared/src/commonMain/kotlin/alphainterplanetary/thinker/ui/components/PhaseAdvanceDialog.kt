package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.PhaseStats
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.LocalExtendedColors
import alphainterplanetary.thinker.ui.theme.PhaseStyles
import alphainterplanetary.thinker.util.formatDuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

private const val RowZoomStart = 0.12f

private const val RowStaggerMs = 520

private const val SuggestedPillDelayMs = 280

private const val SmallBurstIntensity = 16

private const val SmallBurstDurationMs = 1200

private const val CompletedBurstIntensity = 46

private const val CompletedBurstDurationMs = 2000

/**
 * The wrap-up celebration dialog. Plays a level-up sequence over the project's
 * phases (library order): each phase row pops in with a small confetti burst in
 * that phase's colors, the just-completed phase with a slightly larger one.
 * The suggested next phases are visible from the start (their pills stagger in alongside the timeline) — no skip affordance needed. If
 * [suggestionsLoading], a placeholder stands in for the pills. Tapping one of
 * the pills fires [onAdvance]; anything else dismisses with no mutation so the
 * gate button can reopen it and replay.
 */
@Composable
fun PhaseAdvanceDialog(
  phaseStats: List<PhaseStats>,
  completedPhase: Phase,
  suggestions: List<Phase>,
  suggestionsLoading: Boolean = false,
  onAdvance: (Phase) -> Unit,
  onDismiss: () -> Unit,
) {
  var progress by remember { mutableFloatStateOf(0f) }
  val levelUpDurationMs = (phaseStats.size * RowStaggerMs).coerceAtLeast(RowStaggerMs)

  LaunchedEffect(Unit) {
    val start = withFrameNanos { it }
    while (true) {
      val frame = withFrameNanos { it }
      val elapsedMs = (frame - start) / 1_000_000f
      if (elapsedMs >= levelUpDurationMs) break
      progress = elapsedMs / levelUpDurationMs
    }
    progress = 1f
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(
          text = "${completedPhase.label} complete!",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        Text(
          text = "Here's your planning stats so far",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    text = {
      Column {
        phaseStats.forEachIndexed { index, stats ->
          PhaseTimelineRow(
            stats = stats,
            activated = progress >= index.toFloat() / phaseStats.size,
            isCompleted = stats.phase == completedPhase,
          )
        }
        Spacer(modifier = Modifier.height(Dimens.SectionGap))
        if (suggestions.isNotEmpty() || suggestionsLoading) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Choose the next phase",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
            if (suggestionsLoading) {
              // TODO(phase-3): suggestions are currently synchronous
              // (`Project.nextPhaseSuggestions`). When the generation-task
              // framework (IMPLEMENTATION-PLAN.md Phase 3) scores the next
              // phase off the LLM, feed the dialog an async stream instead —
              // e.g. a `StateFlow<List<Phase>?>` from the ViewModel (null =
              // computing): flip `suggestionsLoading` while the LLM is
              // scoring so this placeholder shows, then swap in the resolved
              // list through `suggestions` when it arrives. The pills stay
              // visible (and stagger) the whole time, so no skip gesture is
              // needed.
              Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.IconSizeMedium))
                Spacer(modifier = Modifier.height(Dimens.TightGap))
                Text(
                  text = "Thinking about what's next…",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else {
              suggestions.forEachIndexed { index, phase ->
                SuggestedPhasePill(
                  phase = phase,
                  index = index,
                  onClick = { onAdvance(phase) },
                )
                Spacer(modifier = Modifier.height(Dimens.ContentGap))
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Not now")
      }
    },
  )
}

@Composable
private fun PhaseTimelineRow(
  stats: PhaseStats,
  activated: Boolean,
  isCompleted: Boolean,
) {
  val style = PhaseStyles.forPhase(stats.phase)
  val accent = LocalExtendedColors.current.celebrationAccent
  val scale = remember { Animatable(RowZoomStart) }
  var showBurst by remember { mutableStateOf(false) }

  LaunchedEffect(activated) {
    if (!activated) {
      scale.snapTo(RowZoomStart)
      showBurst = false
    } else {
      scale.animateTo(
        targetValue = 1f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMedium / 4f,
        ),
      )
      showBurst = true
    }
  }

  Row(
    modifier = Modifier
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = scale.value.coerceAtMost(1f)
      },
      // .padding(vertical = Dimens.TightGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(Dimens.ConfettiBurstWidth, Dimens.ConfettiBurstHeight),
      contentAlignment = Alignment.Center,
    ) {
      PhaseBadge(phase = stats.phase)
      if (showBurst) {
        ConfettiBurst(
          colors = if (isCompleted) {
            listOf(style.container, style.content, accent)
          } else {
            listOf(style.container, style.content)
          },
          intensity = if (isCompleted) CompletedBurstIntensity else SmallBurstIntensity,
          durationMs = if (isCompleted) CompletedBurstDurationMs else SmallBurstDurationMs,
          burstPoint = Offset(0.5f, 0.5f),
          modifier = Modifier.size(Dimens.ConfettiBurstWidth, Dimens.ConfettiBurstHeight),
        )
      }
    }
    Column {
      Text(
        text = stats.phase.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "${stats.resolved} of ${stats.total} answered · ${formatDuration(stats.spent)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SuggestedPhasePill(
  phase: Phase,
  index: Int,
  onClick: () -> Unit,
) {
  val scale = remember { Animatable(0.7f) }
  LaunchedEffect(Unit) {
    delay(index * SuggestedPillDelayMs.toLong())
    scale.animateTo(
      targetValue = 1f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium / 2f,
      ),
    )
  }
  PhasePill(
    phase = phase,
    modifier = Modifier.scale(scale.value).clickable { onClick() },
  )
}