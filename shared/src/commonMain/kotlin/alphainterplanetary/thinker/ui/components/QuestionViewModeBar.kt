package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionViewModeBar(
  selectedView: QuestionViewMode,
  onViewSelected: (QuestionViewMode) -> Unit,
) {
  SubcomposeLayout(
    modifier = Modifier.fillMaxWidth(),
  ) { constraints ->
    val meetingGap = Dimens.LabelChipGap.roundToPx()
    val horizontalPadding = Dimens.ScreenPadding.roundToPx()
    val availableInnerWidth = (constraints.maxWidth - horizontalPadding * 2).coerceAtLeast(0)

    val labelWidth = subcompose("label") {
      Text("Questions:", style = MaterialTheme.typography.titleMedium)
    }.first().measure(Constraints()).width

    val chipsWidth = subcompose("chips") {
      Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        QuestionViewMode.values().forEach { mode ->
          FilterChip(
            selected = selectedView == mode,
            onClick = { onViewSelected(mode) },
            label = { Text(mode.displayName) },
            elevation = null,
          )
        }
      }
    }.first().measure(Constraints()).width

    if (labelWidth + meetingGap + chipsWidth <= availableInnerWidth) {
      val placeable = subcompose("wide") {
        WideViewModeBar(
          selectedView = selectedView,
          onViewSelected = onViewSelected,
        )
      }.first().measure(constraints)
      layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
      }
    } else {
      val placeable = subcompose("compact") {
        CompactViewModeBar(
          selectedView = selectedView,
          onViewSelected = onViewSelected,
        )
      }.first().measure(constraints)
      layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
      }
    }
  }
}

@Composable
private fun WideViewModeBar(
  selectedView: QuestionViewMode,
  onViewSelected: (QuestionViewMode) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.ModeBarVerticalPadding),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Questions:", style = MaterialTheme.typography.titleMedium)

    Row(
      horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      QuestionViewMode.values().forEach { mode ->
        FilterChip(
          selected = selectedView == mode,
          onClick = { onViewSelected(mode) },
          label = { Text(mode.displayName) },
          elevation = null,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactViewModeBar(
  selectedView: QuestionViewMode,
  onViewSelected: (QuestionViewMode) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.ModeBarVerticalPadding),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Questions:", style = MaterialTheme.typography.titleMedium)

    Box {
      FilterChip(
        selected = true,
        onClick = { expanded = true },
        label = { Text(selectedView.displayName) },
        elevation = null,
        trailingIcon = {
          Icon(
            if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = null,
          )
        },
      )
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
      ) {
        QuestionViewMode.values().forEach { mode ->
          DropdownMenuItem(
            text = { Text(mode.displayName) },
            onClick = {
              onViewSelected(mode)
              expanded = false
            },
          )
        }
      }
    }
  }
}