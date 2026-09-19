package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.PhaseStats
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.ui.theme.LocalExtendedColors
import alphainterplanetary.thinker.ui.theme.PhaseStyles
import alphainterplanetary.thinker.util.formatDuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow

private const val RowZoomStart = 0.12f

private const val RowStaggerMs = 520

private const val SmallBurstIntensity = 16

private const val SmallBurstDurationMs = 1200

private const val CompletedBurstIntensity = 46

private const val CompletedBurstDurationMs = 2000

/**
 * Which way a callout's arrow points. [Left] is a side callout (arrow on the
 * left edge, pointing at the referenced row next to it); [Up] is a callout
 * rendered below its target (arrow on the top edge, pointing up at it).
 */
private enum class CalloutArrow { Left, Up }

/** Draws the row-tint callout background (arrow + rounded rect) for [arrow]. */
private fun DrawScope.drawCalloutShape(
  arrow: CalloutArrow,
  arrowCoordinate: Float,
  tint: Color,
) {
  val arrowWidth = Dimens.PhaseChoiceArrowWidth.toPx()
  val radius = Dimens.PhaseChoiceCornerRadius.toPx()
  when (arrow) {
    CalloutArrow.Left -> {
      val tipY = arrowCoordinate.coerceIn(0f, size.height)
      val baseTop = (tipY - arrowWidth).coerceIn(0f, size.height)
      val baseBottom = (tipY + arrowWidth).coerceIn(0f, size.height)
      val path = Path().apply {
        moveTo(0f, tipY)
        lineTo(arrowWidth, baseTop)
        lineTo(arrowWidth, baseBottom)
        close()
      }
      drawPath(path = path, color = tint)
      drawRoundRect(
        color = tint,
        topLeft = Offset(arrowWidth, 0f),
        size = Size(size.width - arrowWidth, size.height),
        cornerRadius = CornerRadius(radius),
      )
    }
    CalloutArrow.Up -> {
      val tipX = arrowCoordinate.coerceIn(arrowWidth, size.width - arrowWidth)
      val path = Path().apply {
        moveTo(tipX, 0f)
        lineTo(tipX - arrowWidth, arrowWidth)
        lineTo(tipX + arrowWidth, arrowWidth)
        close()
      }
      drawPath(path = path, color = tint)
      drawRoundRect(
        color = tint,
        topLeft = Offset(0f, arrowWidth),
        size = Size(size.width, size.height - arrowWidth),
        cornerRadius = CornerRadius(radius),
      )
    }
  }
}

/** Content padding that clears the arrow notch for [arrow]. */
private fun calloutContentPadding(arrow: CalloutArrow): PaddingValues = when (arrow) {
  CalloutArrow.Left -> PaddingValues(
    start = Dimens.PhaseChoiceArrowWidth + Dimens.ContentGap,
    top = Dimens.ContentGap,
    end = Dimens.ContentGap,
    bottom = Dimens.ContentGap,
  )
  CalloutArrow.Up -> PaddingValues(
    top = Dimens.PhaseChoiceArrowWidth + Dimens.ContentGap,
    start = Dimens.ContentGap,
    end = Dimens.ContentGap,
    bottom = Dimens.ContentGap,
  )
}

/**
 * The wrap-up celebration dialog. Plays a level-up sequence over the project's
 * phases (library order): each phase row pops in with a small confetti burst in
 * that phase's colors, the just-completed phase with a slightly larger one.
 * The completed phase's [Phase.completedDescription] reads inside an
 * arrow-tipped callout that hugs the row to its right, tinted with the row's
 * tint. Below, the next-phase suggestions come in asynchronously: while
 * [suggestionsLoading] a placeholder stands in for the pills. Each suggested
 * phase sits as a pill; tapping one opens a two-stage choice — an arrow-tipped
 * callout tinted with the phase's row tint zooms in to the right of the pills,
 * spanning the rows and carrying that phase's [Phase.description] plus an
 * "Enter phase" confirmation. Tapping another pill slides the arrow to the
 * newly selected row and re-runs the zoom. When the dialog is too narrow for
 * side callouts, both callouts instead expand beneath their targets (arrow up,
 * nudging following content down); in the next-phase section the expansion is
 * inserted under the tapped pill. The confirmation fires [onAdvance] and
 * dismisses the dialog; anything else dismisses with no mutation so the gate
 * button can reopen it and replay.
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
      Text(
        text = "${completedPhase.label} complete!",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = "Here's your planning stats so far",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Dimens.SectionGap))
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
            horizontalAlignment = Alignment.Start,
          ) {
            Text(
              text = "Choose the next phase",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
            if (suggestionsLoading) {
              // TODO(phase-3): suggestions currently resolve synchronously
              // (`Project.nextPhaseSuggestions`). When the generation-task
              // framework (IMPLEMENTATION-PLAN.md Phase 3) scores the next
              // phase off the LLM, feed the dialog an async stream instead —
              // e.g. a `StateFlow<List<Phase>?>` from the ViewModel (null =
              // computing): the load is kicked off as the level-up timeline
              // plays so it lands in time for the chooser without a gap.
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
              NextPhaseChoices(
                suggestions = suggestions,
                onAdvance = { phase ->
                  onAdvance(phase)
                  onDismiss()
                },
              )
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
  modifier: Modifier = Modifier,
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

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = scale.value.coerceAtMost(1f)
      },
  ) {
    val sideBySide = maxWidth >= Dimens.SideBySideCalloutMinWidth
    Column {
      Row(
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
        Column(
          modifier = Modifier.weight(1f),
        ) {
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
        if (isCompleted && sideBySide) {
          CompletedPhaseCallout(
            phase = stats.phase,
            arrow = CalloutArrow.Left,
            modifier = Modifier
              .padding(start = Dimens.ContentGap)
              .widthIn(max = Dimens.CompletedPhaseCalloutMaxWidth),
          )
        }
      }
      if (isCompleted && !sideBySide) {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        val badgeCenterX = with(LocalDensity.current) {
          Dimens.ConfettiBurstWidth.toPx() / 2f
        }
        AnimatedVisibility(
          visible = activated,
          enter = expandVertically() + fadeIn(),
        ) {
          CompletedPhaseCallout(
            phase = stats.phase,
            arrow = CalloutArrow.Up,
            arrowCoordinate = badgeCenterX,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

/**
 * Tinted callout carrying a completed phase's [Phase.completedDescription].
 * Mirrors [PhaseChoiceCallout]: a rounded rect with a small arrow pointing at
 * the phase's row. For [CalloutArrow.Left] (side callout) the arrow sits at the
 * box's vertical center; for [CalloutArrow.Up] (callout below its row) it sits
 * at [arrowCoordinate] on the top edge, pointing up at the row's badge.
 */
@Composable
private fun CompletedPhaseCallout(
  phase: Phase,
  arrow: CalloutArrow,
  arrowCoordinate: Float = 0f,
  modifier: Modifier = Modifier,
) {
  val tint = PhaseStyles.rowTint(phase)
  Box(
    modifier = modifier
      .drawBehind {
        val coordinate = if (arrow == CalloutArrow.Left) size.height / 2f else arrowCoordinate
        drawCalloutShape(arrow = arrow, arrowCoordinate = coordinate, tint = tint)
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = phase.completedDescription,
      modifier = Modifier.padding(calloutContentPadding(arrow)),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun NextPhaseChoices(
  suggestions: List<Phase>,
  onAdvance: (Phase) -> Unit,
) {
  var selectedNextPhase by remember { mutableStateOf<Phase?>(null) }
  val containerCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
  val pillRects = remember { mutableStateMapOf<Phase, Rect>() }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Dimens.NextPhaseChoicesMinHeight)
      .onGloballyPositioned { containerCoordinates.value = it },
  ) {
    val sideBySide = maxWidth >= Dimens.SideBySideCalloutMinWidth
    if (sideBySide) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
      ) {
        suggestions.forEach { phase ->
          NextPhaseRow(
            phase = phase,
            onSelect = {
              selectedNextPhase = if (selectedNextPhase == phase) null else phase
            },
            onGloballyPositioned = {
              recordPillRect(containerCoordinates.value, it, phase, pillRects)
            },
          )
        }
      }
      val selected = selectedNextPhase
      if (selected != null) {
        val selectedRect = pillRects[selected]
        if (selectedRect != null) {
          val startPadding = with(LocalDensity.current) {
            (pillRects.values.maxOf { it.right } + Dimens.ContentGap.toPx()).toDp()
          }
          key(selected) {
            PhaseChoiceCallout(
              phase = selected,
              arrow = CalloutArrow.Left,
              arrowCoordinate = selectedRect.center.y,
              onConfirm = { onAdvance(selected) },
              modifier = Modifier
                .matchParentSize()
                .padding(start = startPadding),
            )
          }
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
      ) {
        suggestions.forEach { phase ->
          NextPhaseRow(
            phase = phase,
            onSelect = {
              selectedNextPhase = if (selectedNextPhase == phase) null else phase
            },
            onGloballyPositioned = {
              recordPillRect(containerCoordinates.value, it, phase, pillRects)
            },
          )
          if (selectedNextPhase == phase) {
            key(phase) {
              AnimatedVisibility(
                visible = true,
                enter = expandVertically() + fadeIn(),
              ) {
                val pillCenterX = pillRects[phase]?.center?.x
                if (pillCenterX != null) {
                  PhaseChoiceCallout(
                    phase = phase,
                    arrow = CalloutArrow.Up,
                    arrowCoordinate = pillCenterX,
                    onConfirm = { onAdvance(phase) },
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/** Records [coordinates]' bounds in container-space into [out] for [phase]. */
private fun recordPillRect(
  containerCoordinates: LayoutCoordinates?,
  coordinates: LayoutCoordinates,
  phase: Phase,
  out: MutableMap<Phase, Rect>,
) {
  val container = containerCoordinates ?: return
  val topLeft = container.localPositionOf(coordinates, Offset.Zero)
  val bottomRight = container.localPositionOf(
    coordinates,
    Offset(
      x = coordinates.size.width.toFloat(),
      y = coordinates.size.height.toFloat(),
    ),
  )
  out[phase] = Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
}

@Composable
private fun NextPhaseRow(
  phase: Phase,
  onSelect: () -> Unit,
  onGloballyPositioned: (LayoutCoordinates) -> Unit,
) {
  PhasePill(
    phase = phase,
    modifier = Modifier
      .clip(BadgeShape)
      .clickable { onSelect() }
      .onGloballyPositioned(onGloballyPositioned),
  )
}

@Composable
private fun PhaseChoiceCallout(
  phase: Phase,
  arrow: CalloutArrow,
  arrowCoordinate: Float,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tint = PhaseStyles.rowTint(phase)
  val scale = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    scale.animateTo(
      targetValue = 1f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
      ),
    )
  }
  Box(
    modifier = modifier
      .graphicsLayer {
        val originFraction = if (arrow == CalloutArrow.Up) {
          (arrowCoordinate / size.width).coerceIn(0f, 1f)
        } else {
          (arrowCoordinate / size.height).coerceIn(0f, 1f)
        }
        val originX = if (arrow == CalloutArrow.Up) originFraction else 0f
        val originY = if (arrow == CalloutArrow.Up) 0f else originFraction
        scaleX = scale.value
        scaleY = scale.value
        alpha = scale.value
        transformOrigin = TransformOrigin(originX, originY)
      }
      .drawBehind {
        drawCalloutShape(arrow = arrow, arrowCoordinate = arrowCoordinate, tint = tint)
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    Column(
      modifier = Modifier.padding(calloutContentPadding(arrow)),
    ) {
      Text(
        text = phase.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Button(onClick = onConfirm) {
        Text("Enter phase")
      }
    }
  }
}