package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.ui.viewmodel.SettingsUiState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * The header's settings flyout: an index of everything the chrome can change,
 * with each row showing its current value, over the subscreens it opens.
 *
 * It is an index rather than a form on purpose. A long scroll screen of controls
 * hides the two things a flyout makes obvious: what is set right now, and how
 * many decisions are involved. `Tune` is the anchor rather than ⋮ because ⋮
 * reads as "more actions" and this is settings, not actions.
 *
 * Rows carry no leading icon (the value is the point, and three repeated glyphs
 * per group is noise) and are ordered Status, Settings, Tools.
 */
@Composable
internal fun SettingsFlyoutButton(
  chrome: AppChromeState,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val status = chrome.engineStatus()
  val phaseTheme by chrome.settings.phaseTheme.collectAsState()
  val engineMode by chrome.settings.engineMode.collectAsState()
  val engineDelay by chrome.settings.engineDelay.collectAsState()
  val uiState by chrome.settings.uiState.collectAsState()
  var confirmingSampleProjects by remember { mutableStateOf(false) }

  // The generator reports through the ViewModel's one-shot result, so the result
  // is turned into a snackbar here — in the header, which is composed on every
  // screen — rather than inside the row that started it.
  LaunchedEffect(uiState) {
    when (val state = uiState) {
      is SettingsUiState.Success -> {
        chrome.showMessage(state.message)
        chrome.settings.consumeUiState()
      }

      is SettingsUiState.Error -> {
        chrome.showMessage(state.message)
        chrome.settings.consumeUiState()
      }

      SettingsUiState.Idle, SettingsUiState.Generating -> Unit
    }
  }

  Box(modifier = modifier) {
    IconButton(onClick = { expanded = true }) {
      Icon(Icons.Filled.Tune, contentDescription = "Settings and tools")
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      // Status: the same slots the header pills show, so the flyout and the
      // header cannot disagree about what is in use. Read-only — every row opens
      // the Status sheet, which is where the detail lives.
      EngineCapability.entries.forEach { capability ->
        val slot = status.slot(capability)
        FlyoutRow(
          title = capability.displayName(),
          value = slot.displayDetail(),
          onClick = {
            expanded = false
            chrome.openSheet(ChromeSheet.Status)
          },
        )
      }

      HorizontalDivider()
      FlyoutRow(
        title = "Phase colors",
        value = phaseTheme.label,
        onClick = {
          expanded = false
          chrome.openSheet(ChromeSheet.PhaseColors)
        },
      )
      FlyoutRow(
        title = "Intelligence",
        value = engineMode.label,
        onClick = {
          expanded = false
          chrome.openSheet(ChromeSheet.Intelligence)
        },
      )
      FlyoutRow(
        title = "Testing",
        value = if (engineDelay.enabled) "On" else "Off",
        onClick = {
          expanded = false
          chrome.openSheet(ChromeSheet.Testing)
        },
      )

      HorizontalDivider()
      FlyoutRow(
        title = "Activity Log",
        onClick = {
          expanded = false
          chrome.onOpenActivityLog()
        },
      )
      FlyoutRow(
        title = "Task Manager",
        onClick = {
          expanded = false
          chrome.onOpenTaskManager()
        },
      )
      FlyoutRow(
        title = "Generate sample projects",
        onClick = {
          expanded = false
          confirmingSampleProjects = true
        },
      )
    }
  }

  if (confirmingSampleProjects) {
    AlertDialog(
      onDismissRequest = { confirmingSampleProjects = false },
      title = { Text("Generate sample projects?") },
      text = {
        Text(
          "Creates three projects to explore the UI: one sparse, one mostly " +
            "complete, and one with very long text in every field to " +
            "stress-test the layout. Projects already in the list are kept.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            confirmingSampleProjects = false
            chrome.settings.generateSampleProjects()
          },
        ) {
          Text("Generate")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmingSampleProjects = false }) {
          Text("Cancel")
        }
      },
    )
  }
}

/** One flyout row: title, current value, and the chevron that says it opens more. */
@Composable
private fun FlyoutRow(
  title: String,
  value: String? = null,
  onClick: () -> Unit,
) {
  DropdownMenuItem(
    text = {
      Row(verticalAlignment = Alignment.CenterVertically) {
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
        }
      }
    },
    trailingIcon = {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
      )
    },
    onClick = onClick,
  )
}
