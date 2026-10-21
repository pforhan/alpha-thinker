package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineAvailability
import alphainterplanetary.thinker.engine.EngineAvailability.Available
import alphainterplanetary.thinker.engine.EngineAvailability.NotImplemented
import alphainterplanetary.thinker.engine.EngineAvailability.Unavailable
import alphainterplanetary.thinker.engine.EngineCatalog
import alphainterplanetary.thinker.engine.EngineCatalogEntry
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.EngineTier
import alphainterplanetary.thinker.ui.components.OptionHeader
import alphainterplanetary.thinker.ui.components.SelectionDot
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** One tier's entries, as [EngineCatalog.groupedByTier] returns them. */
private typealias EngineGroup = Pair<EngineTier, List<EngineCatalogEntry>>

/** The tiers shown as preferred cards, in the order the row renders them. */
private val PreferredTiers = listOf(EngineTier.Offline, EngineTier.Free, EngineTier.Local)

/**
 * The planning-backend picker: the one place the engine can change, whether it
 * is opened from the header, from the Status sheet's "Change engine…", or over
 * the app's first frames (ENGINE-CONFIG-PLAN.md stages 3c/3d).
 *
 * It is a projection of [EngineCatalog.groupedByTier] over the user's
 * instances: a **Preferred** row of three large cards — Offline, Free, Local —
 * over an **Advanced** disclosure holding the on-device runtime and
 * [ConnectionsList] (3b's list verbatim, so a tap selects and the per-row icons
 * open the add/edit dialog). The split is the whole new-user story: the common
 * choices are named cards with one-line taglines, and the ever-growing list of
 * endpoints is behind a header that says it takes configuring.
 *
 * A preferred tier with no selectable entry is a card that says so rather than
 * an absent card, so the row keeps its three shapes as backends land. Free and
 * Local are placeholders today, and both wear the same treatment — a muted
 * card with the reason underneath and no select target — rather than each
 * inventing its own copy. The Local card is where the recommendation and
 * download dialog will live (MODEL-CATALOG-PLAN.md stage 3); nothing here has
 * to move when it arrives, because the card already owns that tier.
 */
@Composable
internal fun EnginePickerSheetContent(chrome: AppChromeState) {
  val selectedRef by chrome.engines.selectedRef.collectAsState()
  val instances by chrome.engines.instances.collectAsState()
  val lastUsedAt by chrome.engines.lastUsedAt.collectAsState()
  val draft by chrome.engines.draft.collectAsState()
  val draftModels by chrome.engines.draftModels.collectAsState()
  val engines = chrome.engines

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
    PreferredEngines(
      groups = groups,
      selectedRef = selectedRef,
      availability = engines::availability,
      onSelect = engines::selectEngine,
    )
    AdvancedEngines(
      groups = groups,
      instances = instances,
      selectedRef = selectedRef,
      advancedSelected = groups
        .firstOrNull { it.first == EngineTier.Advanced }
        ?.second
        ?.any { it.ref == selectedRef } == true,
      availability = engines::availability,
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
      onSave = engines::saveDraft,
      onDismiss = engines::cancelDraft,
    )
  }
}

/**
 * The preferred tiers as one row of equal-width, equal-height cards. Height is
 * matched by [IntrinsicSize.Min] on the row, so the three cards share the
 * tallest one's height rather than each stopping at its own text: the cards are
 * read as a set, and a ragged bottom edge makes the shortest one look like the
 * lesser choice.
 */
@Composable
private fun PreferredEngines(
  groups: List<EngineGroup>,
  selectedRef: EngineRef,
  availability: (EngineCatalogEntry) -> EngineAvailability,
  onSelect: (EngineRef) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap)) {
    TierHeader(label = "Preferred")
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
  tier: EngineTier,
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
  availability is NotImplemented -> "Not available on this device yet."
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
 * The Advanced disclosure: the configured connections and the system
 * on-device runtime.
 *
 * The built-in rows come first (the on-device runtime is the one non-Remote
 * Advanced entry today) and the connections follow as separate rows of their
 * own — there is no "Remote" card or heading grouping them: the two are
 * different kinds of thing, and a heading for the kind would be a label the
 * user never chose between. Selection is the [SelectionDot] every row carries,
 * and the row's own icons edit and delete it. It opens by default when the
 * running engine is one of these, so a user who has already gone advanced does
 * not have to find it again.
 *
 * The on-device runtime may later become a subset of the Local tier rather
 * than a row here; it lives in this area until that UI is ready.
 */
@Composable
private fun AdvancedEngines(
  groups: List<EngineGroup>,
  instances: List<EngineInstance>,
  selectedRef: EngineRef,
  advancedSelected: Boolean,
  availability: (EngineCatalogEntry) -> EngineAvailability,
  onSelect: (EngineRef) -> Unit,
  onEdit: (EngineRef.Instance) -> Unit,
  onDelete: (EngineRef.Instance) -> Unit,
  onAdd: () -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(advancedSelected) }

  Column(verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = EngineTier.Advanced.label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Hide" else "Show")
      }
    }
    if (expanded) {
      groups.firstOrNull { it.first == EngineTier.Advanced }?.second
        .orEmpty()
        .filter { it.kind != EngineKind.Remote }
        .forEach { entry ->
          AdvancedBuiltinRow(
            entry = entry,
            selected = entry.ref == selectedRef,
            availability = availability(entry),
            onSelect = { onSelect(entry.ref) },
          )
        }
      ConnectionsList(
        instances = instances,
        selectedRef = selectedRef,
        onSelect = onSelect,
        onEdit = onEdit,
        onDelete = onDelete,
        onAdd = onAdd,
      )
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

/**
 * A non-Remote built-in in the disclosure — today only the system on-device
 * runtime. A card so it matches the connection cards below it, and takes the
 * same [selectedEngineColors] fill when it is the running engine.
 */
@Composable
private fun AdvancedBuiltinRow(
  entry: EngineCatalogEntry,
  selected: Boolean,
  availability: EngineAvailability,
  onSelect: () -> Unit,
) {
  val selectable = availability is Available
  Card(
    onClick = onSelect,
    enabled = selectable,
    modifier = Modifier.fillMaxWidth(),
    colors = selectedEngineColors(selected),
  ) {
    Column(
      modifier = Modifier.padding(Dimens.CardPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
    ) {
      OptionHeader(
        label = entry.label,
        description = entry.description,
      ) {
        SelectionDot(selected)
      }
      if (!selectable) {
        PlaceholderNote("Not available on this device yet.")
      }
    }
  }
}
