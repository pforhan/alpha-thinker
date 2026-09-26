package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineDelayConfig
import alphainterplanetary.thinker.engine.EngineInteraction
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * The Task-Manager testing controls: a master switch that, while enabled,
 * expands into a per-interaction picker choosing each PlanningEngine
 * delay from the 0s (off) / 2s / 5s / 30s options.
 */
@Composable
internal fun DelayControlItem(
  config: EngineDelayConfig,
  onEnabledChange: (Boolean) -> Unit,
  onDelayChange: (EngineInteraction, Int) -> Unit,
  onExpandedHeightChange: (Int) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Slow down the planning engine",
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = "Adds an artificial delay to each PlanningEngine interaction so " +
              "Task Manager tasks stay visible long enough to observe them.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
        Switch(
          checked = config.enabled,
          onCheckedChange = onEnabledChange,
        )
      }
      if (config.enabled) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onExpandedHeightChange(it.height) },
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Dimens.ContentGap))
            EngineInteraction.entries.forEachIndexed { index, interaction ->
              if (index > 0) {
                Spacer(modifier = Modifier.height(Dimens.ContentGap))
              }
              DelayChoiceRow(
                interaction = interaction,
                secondsByInteraction = config.secondsByInteraction,
                onDelayChange = onDelayChange,
              )
            }
          }
        }
      }
    }
  }
}

/** One PlanningEngine interaction: its label plus a 0s / 2s / 5s / 30s choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DelayChoiceRow(
  interaction: EngineInteraction,
  secondsByInteraction: Map<EngineInteraction, Int>,
  onDelayChange: (EngineInteraction, Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = interaction.label,
      style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(Dimens.TightGap))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      EngineDelayConfig.DelayOptionsSeconds.forEach { seconds ->
        FilterChip(
          selected = secondsByInteraction[interaction] == seconds,
          onClick = { onDelayChange(interaction, seconds) },
          label = { Text("${seconds}s") },
          elevation = null,
        )
      }
    }
  }
}
