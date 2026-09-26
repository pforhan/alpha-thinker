package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.activitylog.ActivityRecord
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * The status detail behind the header's status cluster: the engine that will
 * run, what it will and will not do, and how the last activity went.
 *
 * It is read-only on purpose. The one control that can change any of this — the
 * engine picker — lives in the [ChromeSheet.Intelligence] sheet, one row away, so
 * the cluster and the flyout's rows cannot each grow a switch the other lacks.
 */
@Composable
internal fun StatusSheetContent(
  status: EngineStatus,
  latestActivity: ActivityRecord?,
  onOpenIntelligence: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    Text(
      text = status.mode.label,
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = status.mode.description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    status.slots.forEach { slot ->
      CapabilityRow(slot = slot)
    }

    if (latestActivity != null) {
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      HorizontalDivider()
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        // The one place the header says how the last run *went* rather than what
        // is configured, so a fully set-up mode that still failed is not hidden.
        text = "Last activity: ${latestActivity.summary}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (latestActivity.hasError) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      )
    }

    HorizontalDivider()
    SheetLinkRow(
      title = "Change engine…",
      onClick = onOpenIntelligence,
    )
  }
}

/**
 * One capability: its name, whether the app will use it, and the value behind
 * that (the endpoint, the model).
 */
@Composable
private fun CapabilityRow(slot: CapabilityStatus) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = slot.capability.displayName(),
        style = MaterialTheme.typography.titleSmall,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = slot.state.readout(),
        style = MaterialTheme.typography.bodySmall,
        color = slot.state.readoutColor(),
      )
    }
    Spacer(modifier = Modifier.width(Dimens.ContentGap))
    Text(
      text = slot.displayDetail(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}

/** A row that opens another sheet: title, optional current value, chevron. */
@Composable
internal fun SheetLinkRow(
  title: String,
  value: String? = null,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Dimens.ActionRowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    if (value != null) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
