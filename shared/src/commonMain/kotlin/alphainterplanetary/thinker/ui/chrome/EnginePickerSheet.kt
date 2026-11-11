package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.config.EngineAvailability
import alphainterplanetary.thinker.engine.config.EngineAvailability.Available
import alphainterplanetary.thinker.engine.config.EngineAvailability.Unavailable
import alphainterplanetary.thinker.engine.config.EngineCatalog
import alphainterplanetary.thinker.engine.config.EngineCatalogEntry
import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EnginePickerTier
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.ui.components.SelectionDot
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** One tier's entries, as [EngineCatalog.groupedByTier] returns them. */
private typealias EngineGroup = Pair<EnginePickerTier, List<EngineCatalogEntry>>

/** The tiers shown as preferred cards, in the order the row renders them. */
private val PreferredTiers = listOf(EnginePickerTier.Offline, EnginePickerTier.Free)

/**
 * The planning-backend picker: the one place the engine can change, whether it
 * is opened from the header, from the Status sheet's "Change engine…", or over
 * the app's first frames (ENGINE-CONFIG-PLAN.md stages 3c/3d).
 *
 * It is a projection of [EngineCatalog.groupedByTier] over the user's
 * instances: a **Preferred** row of two large cards — Offline and Free — over
 * the **Custom** connections (a tap selects and the per-row icons open the
 * add/edit dialog) and an add-connection action. The split keeps the common
 * choices as named cards with one-line taglines, with user-configured
 * endpoints listed below; when there are no custom engines, only the
 * add-connection button is shown.
 *
 * A preferred tier with no selectable entry is a card that says so rather than
 * an absent card, so the row keeps its shapes as backends land.
 */
@Composable
internal fun EnginePickerSheetContent(chrome: AppChromeState) {
  val selectedRef by chrome.engines.selectedRef.collectAsState()
  val instances by chrome.engines.instances.collectAsState()
  val lastUsedAt by chrome.engines.lastUsedAt.collectAsState()
  val draft by chrome.engines.draft.collectAsState()
  val draftModels by chrome.engines.draftModels.collectAsState()
  val pulls by chrome.engines.downloads.collectAsState()
  val firstRunIntro by chrome.engines.firstRunIntro.collectAsState()
  val engines = chrome.engines

  DisposableEffect(engines) {
    onDispose { engines.clearFirstRunIntro() }
  }

  val groups = remember(instances, lastUsedAt) {
    EngineCatalog.groupedByTier(instances, lastUsedAt)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    if (firstRunIntro) {
      FirstRunIntro()
    }
    PreferredEngines(
      groups = groups,
      selectedRef = selectedRef,
      availability = engines::availability,
      onSelect = engines::selectEngine,
    )
    CustomEngines(
      instances = instances,
      selectedRef = selectedRef,
      onSelect = engines::selectEngine,
      onEdit = { ref -> engines.startEditConnection(ref.id) },
      onDelete = { ref -> engines.deleteInstance(ref.id) },
      onAdd = { engines.startAddConnection() },
    )
  }

  draft?.let { current ->
    ConnectionDialog(
      draft = current,
      isNew = instances.none { it.id == current.id },
      models = draftModels,
      onNameChange = engines::setDraftLabel,
      onProviderChange = engines::setDraftProvider,
      onBaseUrlChange = engines::setDraftBaseUrl,
      onApiKeyChange = engines::setDraftApiKey,
      onModelChange = engines::setDraftModel,
      onContextTokensChange = engines::setDraftContextTokens,
      onBrowseModels = engines::refreshDraftModels,
      onModelSelected = { info ->
        engines.pickDraftModel(info.id, info.contextLength)
      },
      onPullModel = { modelId ->
        engines.pullOllamaModel(modelId, draft?.baseUrl)
      },
      onCancelPull = engines::cancelDownload,
      pulls = pulls,
      onSave = engines::saveDraft,
      onDismiss = engines::cancelDraft,
    )
  }
}

/**
 * The preferred tiers as one row of equal-width, equal-height cards. Height is
 * matched by [IntrinsicSize.Min] on the row, so the cards share the tallest
 * one's height rather than each stopping at its own text: the cards are read as
 * a set, and a ragged bottom edge makes the shortest one look like the lesser
 * choice.
 */
@Composable
private fun PreferredEngines(
  groups: List<EngineGroup>,
  selectedRef: EngineRef,
  availability: (EngineCatalogEntry) -> EngineAvailability,
  onSelect: (EngineRef) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap)) {
    TierHeader(label = "Recommended")
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
    ) {
      PreferredTiers.forEach { tier ->
        val entry = groups.firstOrNull { it.first == tier }?.second?.firstOrNull()
        PreferredEngineCard(
          tier = tier,
          entry = entry,
          selected = entry != null && entry.ref == selectedRef,
          availability = entry?.let(availability),
          onSelect = { entry?.let { onSelect(it.ref) } },
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        )
      }
    }
  }
}

/**
 * One preferred engine. Selected, the card takes the primary container — the
 * same fill a selected connection card takes below, so "this is the running
 * engine" is one colour across the whole sheet, and the [SelectionDot] in the
 * top corner is the marker that says so in every case. A placeholder is muted
 * by the disabled card colours and explains itself underneath.
 */
@Composable
private fun PreferredEngineCard(
  tier: EnginePickerTier,
  entry: EngineCatalogEntry?,
  selected: Boolean,
  availability: EngineAvailability?,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val enabled = availability is Available
  Card(
    onClick = onSelect,
    enabled = enabled,
    modifier = modifier,
    colors = selectedEngineColors(selected),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      Row(verticalAlignment = Alignment.Top) {
        Text(
          text = tier.label,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        SelectionDot(selected)
      }
      Text(
        text = tier.tagline.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = if (selected) {
          MaterialTheme.colorScheme.onPrimaryContainer
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
      )
      unavailableNote(entry, availability)?.let { PlaceholderNote(it) }
    }
  }
}

/**
 * Why this card cannot be chosen, or null when it can. The tier owns its copy,
 * so the three cards say the same kind of thing in the same place.
 */
private fun unavailableNote(
  entry: EngineCatalogEntry?,
  availability: EngineAvailability?,
): String? = when {
  entry == null -> "Not available on this device yet."
  availability is Unavailable -> "Coming soon · ${availability.reason}"
  else -> null
}

/** The muted line that says why a card is inert. */
@Composable
private fun PlaceholderNote(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The configured custom connections, always shown.
 *
 * There is no "Remote" card or heading grouping them: a heading for the kind
 * would be a label the user never chose between. Selection is the
 * [SelectionDot] every row carries, and the row's own icons edit and delete
 * it. When there are no custom engines, the section header is omitted and only
 * the add-connection button is shown.
 */
@Composable
private fun CustomEngines(
  instances: List<EngineInstance>,
  selectedRef: EngineRef,
  onSelect: (EngineRef) -> Unit,
  onEdit: (EngineRef.Instance) -> Unit,
  onDelete: (EngineRef.Instance) -> Unit,
  onAdd: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap)) {
    if (instances.isNotEmpty()) {
      TierHeader(label = EnginePickerTier.Custom.label)
      ConnectionsList(
        instances = instances,
        selectedRef = selectedRef,
        onSelect = onSelect,
        onEdit = onEdit,
        onDelete = onDelete,
      )
      Spacer(modifier = Modifier.height(Dimens.ContentGap))
    }
    OutlinedButton(
      onClick = onAdd,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(Icons.Default.Add, contentDescription = null)
      Spacer(modifier = Modifier.width(Dimens.IconLabelGap))
      Text("Add your own connection")
    }
  }
}

/** A section header: the label a group of engine cards sits under. */
@Composable
private fun TierHeader(label: String) {
  Text(
    text = label,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * The explanation shown the first time this picker opens: why an engine must be
 * chosen and what the choice means for where the user's project data goes.
 */
@Composable
private fun FirstRunIntro() {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      Text(
        text = "Choose how Alpha Thinker works",
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = "Pick the engine that plans your projects. Offline runs on this " +
          "device and keeps everything here. A remote connection sends prompts " +
          "to the server you choose, so use it only when you're comfortable " +
          "sharing that data.",
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = "You can change this anytime from the engine status in the top bar.",
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

/**
 * The container a selected engine card takes, wherever it appears: the
 * preferred row and the connection cards in the Advanced area share it, so a
 * running engine is one colour on the whole sheet. Unselected is the card's
 * own default, so an unselected card looks exactly like every other card.
 */
@Composable
internal fun selectedEngineColors(selected: Boolean): CardColors = if (selected) {
  CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
  )
} else {
  CardDefaults.cardColors()
}
