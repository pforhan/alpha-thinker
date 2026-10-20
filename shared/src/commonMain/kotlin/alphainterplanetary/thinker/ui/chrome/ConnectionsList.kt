package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.ui.components.OptionHeader
import alphainterplanetary.thinker.ui.components.SelectionDot
import alphainterplanetary.thinker.ui.theme.Dimens
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The Remote card's body: the user's connections, one [ConnectionRow] each
 * (ENGINE-CONFIG-PLAN.md stage 3b).
 *
 * Tapping a row selects it as the running engine — the selection is only the
 * dot on the row's right edge, in line with the kind cards above and below, so
 * selection stays as subdued as it is for them. Everything else about a
 * connection lives behind the add/edit dialog ([ConnectionDialog]), which
 * holds the fields as a draft and commits them on Save: the row keeps just the
 * edit and delete affordances, and a connection gets a name of the user's own
 * when it is created instead of inheriting the provider's label. The list will
 * be reused verbatim behind the picker sheet's Advanced disclosure (3c), so
 * rows stay compact enough for the expected two or three connections.
 */
@Composable
internal fun ConnectionsList(
  instances: List<EngineInstance>,
  selectedRef: EngineRef,
  onSelect: (EngineRef.Instance) -> Unit,
  onEdit: (EngineRef.Instance) -> Unit,
  onDelete: (EngineRef.Instance) -> Unit,
  onAdd: () -> Unit,
) {
  var deleting by remember { mutableStateOf<EngineInstance?>(null) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      OptionHeader(
        label = EngineKind.Remote.label,
        description = EngineKind.Remote.description,
      ) {
        // A container of connections, not a selectable kind: the rows below
        // carry the selection, so the header marks nothing of its own.
      }
      if (instances.isEmpty()) {
        Spacer(modifier = Modifier.height(Dimens.SectionGap))
        Text(
          text = "No connections yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      instances.forEachIndexed { index, instance ->
        val ref = EngineRef.Instance(instance.id)
        if (index > 0) {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
        }
        ConnectionRow(
          instance = instance,
          selected = selectedRef == ref,
          onSelect = { onSelect(ref) },
          onEdit = { onEdit(ref) },
          onDelete = { deleting = instance },
        )
      }
      Spacer(modifier = Modifier.height(Dimens.SectionGap))
      OutlinedButton(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
        Text("Add connection")
      }
    }
  }

  deleting?.let { target ->
    ConfirmDeleteConnectionDialog(
      instance = target,
      isSelected = selectedRef is EngineRef.Instance && selectedRef.id == target.id,
      onDismiss = { deleting = null },
      onConfirm = {
        onDelete(EngineRef.Instance(target.id))
        deleting = null
      },
    )
  }
}

/**
 * One connection: its label and at-a-glance summary on the leading edge, with
 * edit and delete beside the selection dot on the trailing edge — the dot
 * last, so it lines up with the dots of the kind cards above and below. The
 * whole row is the select target; the icons are the row's own actions.
 */
@Composable
private fun ConnectionRow(
  instance: EngineInstance,
  selected: Boolean,
  onSelect: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OptionHeader(label = instance.label, description = instance.connectionSummary()) {
      RowActionIcon(
        icon = Icons.Default.Edit,
        description = "Edit ${instance.label}",
        onClick = onEdit,
      )
      RowActionIcon(
        icon = Icons.Default.Delete,
        description = "Delete ${instance.label}",
        onClick = onDelete,
      )
      // The same inset [RowActionIcon] gives its icon, so the dot's gap to
      // Delete matches the gap between Edit and Delete.
      Spacer(modifier = Modifier.width(Dimens.ContentGap))
      SelectionDot(selected)
    }
  }
}

/**
 * An icon action sized for a row's trailing edge. [IconButton]'s built-in
 * minimum would space the two icons twice as far apart as the icon sits from
 * the selection dot, so the slot is sized by hand: the icon centered in a
 * square padded by [Dimens.ContentGap], which is also the gap the row leaves
 * between the slots.
 */
@Composable
private fun RowActionIcon(
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(Dimens.IconSizeMedium + Dimens.ContentGap * 2f)
      .clip(MaterialTheme.shapes.small)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      modifier = Modifier.size(Dimens.IconSizeMedium),
    )
  }
}

/**
 * The row's at-a-glance line: the provider and the model it serves, or what is
 * still missing when the connection could not run yet — the same "needs setup"
 * reading the status layer gives an incomplete engine (ENGINE-CONFIG-PLAN.md
 * 3e), phrased here so an unfinished row is obvious where it is edited.
 */
private fun EngineInstance.connectionSummary(): String {
  val providerLabel = provider?.label ?: RemoteProvider.Default.label
  val needsSetup = model.isBlank() || (provider?.requiresApiKey == true && apiKey.isBlank())
  return if (needsSetup) {
    "$providerLabel · needs setup"
  } else {
    "$providerLabel · $model"
  }
}

/** The delete confirmation, which also says when the running engine falls back. */
@Composable
private fun ConfirmDeleteConnectionDialog(
  instance: EngineInstance,
  isSelected: Boolean,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Delete connection?") },
    text = {
      Column {
        Text(instance.label)
        Spacer(modifier = Modifier.height(Dimens.ContentGap))
        Text(
          text = if (isSelected) {
            "The app falls back to the Offline engine. This action cannot be undone."
          } else {
            "This action cannot be undone."
          },
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text("Delete")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}
