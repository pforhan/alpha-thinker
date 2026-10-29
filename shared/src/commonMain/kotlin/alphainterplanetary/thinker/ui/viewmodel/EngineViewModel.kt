package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.downloads.DownloadKind
import alphainterplanetary.thinker.downloads.DownloadState
import alphainterplanetary.thinker.downloads.DownloadStatus
import alphainterplanetary.thinker.downloads.ModelDownloader
import alphainterplanetary.thinker.engine.EngineAvailability
import alphainterplanetary.thinker.engine.EngineCatalogEntry
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.ModelPresentationTier
import alphainterplanetary.thinker.engine.ModelRank
import alphainterplanetary.thinker.engine.ModelRanker
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.EngineRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The engines' ViewModel: which planning backend is selected, the CRUD for
 * user-created instances, and the draft behind the add/edit connection dialog
 * (ENGINE-CONFIG-PLAN.md stage 3b). It is a sibling of [SettingsViewModel]
 * rather than part of it because the engine is a concern of its own — the
 * connections list and the picker sheet (3c) are built on exactly these
 * mutators — and hoisted on the chrome beside the settings for the same reason:
 * the selection and its connection outlive the sheet they are edited in.
 *
 * A connection is edited as a **draft** ([EngineInstance] reused as the working
 * copy): [startAddConnection] / [startEditConnection] open one, the
 * `setDraft*` mutators shape it, and [saveDraft] commits it. Nothing touches
 * the stored engine until Save, so a cancelled dialog changes nothing; saving
 * a new connection selects it — it was made to be used — while saving an edit
 * of another row leaves the running engine alone, as does [deleteInstance],
 * both row-scoped by id.
 */
class EngineViewModel(
  private val engineRepository: EngineRepository,
  private val modelCatalog: RemoteModelCatalog,
  private val scope: CoroutineScope,
  private val modelDownloader: ModelDownloader? = null,
) {
  /** The selected planning backend; only available engines can be chosen. */
  val selectedRef: StateFlow<EngineRef> = engineRepository.selected

  /**
   * Every user-created engine, for the connections list (3b): the rows to
   * render, in creation order, with the newest appended beside the
   * add-connection button.
   */
  val instances: StateFlow<List<EngineInstance>> = engineRepository.instances

  /** When each engine last ran, so the picker can order its tiers by recency (3c). */
  val lastUsedAt: StateFlow<Map<EngineRef, Long>> = engineRepository.lastUsedAt

  /**
   * True on a store that has never recorded an engine choice or run — what
   * opens the picker over the app's first frames (3d). False again as soon as
   * [markFirstRunSeen] runs, so the trigger has one source in the repository
   * and the chrome merely projects it.
   */
  val firstRunPending: StateFlow<Boolean> = engineRepository.firstRunPending

  /** Marks the first-run picker shown; see [firstRunPending] and 3d. */
  fun markFirstRunSeen() {
    engineRepository.markFirstRunSeen()
  }

  /**
   * Evaluates a catalog entry's availability against the live repository —
   * the picker's card enablement, so a placeholder ("coming soon") card is
   * gated off by the same truth the status layer reports.
   */
  fun availability(entry: EngineCatalogEntry): EngineAvailability =
    entry.availability(engineRepository)

  fun selectEngine(ref: EngineRef) {
    engineRepository.select(ref)
  }

  /** The connection dialog's working copy; null while the dialog is closed. */
  private val _draft = MutableStateFlow<EngineInstance?>(null)
  val draft: StateFlow<EngineInstance?> = _draft.asStateFlow()

  /**
   * Opens a fresh draft for a new connection, seeded from [provider]'s starter
   * endpoint, model, and window. The label starts at the provider's
   * [RemoteProvider.suggestedConnectionName] — the dialog's Name field is
   * where it gets a name of the user's own.
   */
  fun startAddConnection(provider: RemoteProvider = RemoteProvider.Default) {
    _draft.value = EngineInstance.remote(provider = provider)
    _draftModels.value = RemoteModelListState.Idle
  }

  /** Opens a draft over the stored instance with [id]; a no-op when absent. */
  fun startEditConnection(id: String) {
    _draft.value = engineRepository.getInstance(id)
    _draftModels.value = RemoteModelListState.Idle
  }

  /** Closes the dialog and discards the draft. */
  fun cancelDraft() {
    _draft.value = null
    _draftModels.value = RemoteModelListState.Idle
  }

  /** Sets the draft's name; blank is rejected at [saveDraft]. */
  fun setDraftLabel(label: String) {
    _draft.update { it?.copy(label = label) }
  }

  /**
   * Switches the draft's Remote protocol and re-seeds the fields the previous
   * provider supplied, so one provider's endpoint, starter model, and window
   * do not ride across into a connection that cannot use them
   * (ENGINE-CONFIG-PLAN.md 2a3).
   *
   * Each field is re-seeded only while it still holds the previous provider's
   * starter value, so a hand-edited endpoint, model, or window is kept. The
   * window also counts as starter while the model is, because it is the number
   * that starter resolves to — the code default, or the window a metadata
   * lookup refined it to ([seedProviderWindow]). The name follows the same
   * rule: while it still holds the previous provider's suggested connection
   * name it moves with the pill, so a draft suggested as "Ollama" does not
   * keep that name after switching to Anthropic; a name the user typed is
   * kept.
   */
  fun setDraftProvider(provider: RemoteProvider) {
    val current = _draft.value ?: return
    val previous = current.provider ?: RemoteProvider.Default
    val reseedLabel = current.label == previous.suggestedConnectionName
    val reseedEndpoint = current.baseUrl == previous.defaultBaseUrl
    val reseedModel = current.model == previous.defaultModel
    val reseedWindow = reseedModel || current.contextTokens == previous.defaultContextTokens

    val switched = current.copy(
      label = if (reseedLabel) provider.suggestedConnectionName else current.label,
      provider = provider,
      baseUrl = if (reseedEndpoint) provider.defaultBaseUrl else current.baseUrl,
      model = if (reseedModel) provider.defaultModel else current.model,
      contextTokens = if (reseedWindow) provider.defaultContextTokens else current.contextTokens,
    )
    _draft.value = switched
    // A model list is only meaningful for the provider it was read from, so a
    // switch discards it rather than leaving the old server's models on screen.
    _draftModels.value = RemoteModelListState.Idle
    if (reseedWindow) seedProviderWindow(provider, switched.baseUrl, switched.model)
  }

  /**
   * Replaces the window [setDraftProvider] just seeded with the one the
   * provider reports for the model, when it can report one. The provider need
   * not answer — an OpenAI-compatible endpoint exposes no per-model window and
   * a local Ollama server may be down — in which case the seeded default
   * stands. The lookup lands asynchronously, so it is applied only while the
   * draft still carries that seeded default; a window typed in the meantime is
   * never overwritten.
   */
  private fun seedProviderWindow(provider: RemoteProvider, baseUrl: String, model: String) {
    scope.launch {
      val window = try {
        modelCatalog.contextLength(provider, baseUrl, model)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      } ?: return@launch
      if (window == provider.defaultContextTokens) return@launch
      _draft.update { live ->
        if (live != null &&
          live.provider == provider &&
          live.model == model &&
          live.contextTokens == provider.defaultContextTokens
        ) {
          live.copy(contextTokens = window)
        } else {
          live
        }
      }
    }
  }

  /** Sets the draft's endpoint. */
  fun setDraftBaseUrl(url: String) {
    _draft.update { it?.copy(baseUrl = url) }
  }

  /** Sets the draft's API key (empty for providers that need none). */
  fun setDraftApiKey(key: String) {
    _draft.update { it?.copy(apiKey = key) }
  }

  /** Sets the draft's model name. */
  fun setDraftModel(model: String) {
    _draft.update { it?.copy(model = model) }
  }

  /**
   * Sets the draft's declared context window — the number a model named in a
   * text field cannot supply for itself, and that the planning-context budget
   * is a share of. Filled in from the provider's catalogue when one can be
   * read ([pickDraftModel]).
   */
  fun setDraftContextTokens(tokens: Int) {
    require(tokens > 0) { "a model context window must be positive: $tokens" }
    _draft.update { it?.copy(contextTokens = tokens) }
  }

  /**
   * Active Ollama pulls / local downloads, projected from the injected
   * [ModelDownloader] (empty when none is wired, e.g. in tests).
   */
  val downloads: StateFlow<List<DownloadState>> =
    modelDownloader?.downloads() ?: MutableStateFlow(emptyList())

  /** What the dialog's model browser is showing, from the last [refreshDraftModels]. */
  private val _draftModels = MutableStateFlow<RemoteModelListState>(RemoteModelListState.Idle)
  val draftModels: StateFlow<RemoteModelListState> = _draftModels.asStateFlow()

  init {
    // A finished Ollama pull changes what the server serves, so the browser's
    // snapshot is stale: re-read it, and the pull chip becomes a normal model
    // instead of enqueuing the same download again. Only a pull this app
    // started can succeed, and refreshing with a dialog open is enough.
    scope.launch {
      var completed = emptySet<String>()
      downloads.collect { states ->
        val succeeded = states
          .filter { it.kind == DownloadKind.OllamaPull && it.status == DownloadStatus.Succeeded }
          .map { it.handle.id }
          .toSet()
        if ((succeeded - completed).isNotEmpty() && _draft.value != null) {
          refreshDraftModels()
        }
        completed = succeeded
      }
    }
  }

  /**
   * Reads the draft provider's model list for the draft endpoint. Instant for
   * a provider with a bundled catalogue (Anthropic); a network round trip for
   * one that has to be asked (Ollama's management API, the generic
   * OpenAI-compatible `GET /v1/models`). A failure is captured in the state
   * rather than thrown, because the browser is an affordance beside a field
   * the user can still type into. When no model is selected or the current
   * selection is missing from the list, auto-selects the top of Recommended
   * (falling back to Others).
   */
  fun refreshDraftModels() {
    val draft = _draft.value ?: return
    val provider = draft.provider ?: return
    _draftModels.value = RemoteModelListState.Loading
    scope.launch {
      val state = try {
        val models = modelCatalog.list(provider, draft.baseUrl, draft.apiKey)
        val ranked = ModelRanker.rank(models)
        maybeAutoSelectBestAvailable(draft, ranked)
        RemoteModelListState.Loaded(models)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        RemoteModelListState.Failed(e.message ?: e.toString())
      }
      _draftModels.value = state
    }
  }

  private fun maybeAutoSelectBestAvailable(draft: EngineInstance, ranked: List<ModelRank>) {
    if (ranked.isEmpty()) return
    val currentModel = draft.model
    val currentExists = currentModel.isNotBlank() && ranked.any { it.model.id == currentModel }
    if (currentExists) return

    val best = ranked.firstOrNull { it.presentationTier == ModelPresentationTier.Recommended }
      ?: ranked.firstOrNull { it.presentationTier == ModelPresentationTier.HighQualityButSlower }
      ?: ranked.firstOrNull { it.presentationTier == ModelPresentationTier.LowerQualityButFast }
      ?: ranked.firstOrNull()
      ?: return

    _draft.update { live ->
      if (live == null || live.provider != draft.provider) {
        live
      } else if (best.model.contextLength != null) {
        live.copy(model = best.model.id, contextTokens = best.model.contextLength)
      } else {
        live.copy(model = best.model.id)
      }
    }
  }

  /**
   * Picks [id] into the draft and, when its window is known, adopts it as the
   * declared context. The window is the reason to browse at all: a catalogue
   * can say how big the model really is — when it can, the budget stops
   * guessing. An OpenAI-compatible listing often cannot (its wire format
   * carries no window), in which case the id still lands.
   */
  fun pickDraftModel(id: String, contextLength: Int?) {
    _draft.update { current ->
      if (current == null) {
        current
      } else if (contextLength != null) {
        current.copy(model = id, contextTokens = contextLength)
      } else {
        current.copy(model = id)
      }
    }
  }

  /**
   * Triggers an Ollama pull for [modelId] against [baseUrl], when a downloader
   * is wired. Selects the model in the draft so saving the dialog keeps the
   * connection pointed at it while the server manages the download.
   */
  fun pullOllamaModel(modelId: String, baseUrl: String? = null) {
    val resolvedBase = baseUrl ?: _draft.value?.baseUrl ?: return
    modelDownloader?.pullOllama(modelId, resolvedBase)
    _draft.update { current ->
      if (current == null) current else current.copy(model = modelId)
    }
  }

  fun pullOllama(modelId: String, baseUrl: String) =
    pullOllamaModel(modelId, baseUrl)

  fun pullCuratedOllamaModel(modelId: String, baseUrl: String? = null) =
    pullOllamaModel(modelId, baseUrl)

  fun startOllamaPull(modelId: String, baseUrl: String? = null) =
    pullOllamaModel(modelId, baseUrl)

  fun triggerOllamaPull(modelId: String, baseUrl: String? = null) =
    pullOllamaModel(modelId, baseUrl)

  fun pull(modelId: String, baseUrl: String? = null) =
    pullOllamaModel(modelId, baseUrl)

  fun pullModel(modelId: String, baseUrl: String? = null) =
    pullOllamaModel(modelId, baseUrl)

  /**
   * Commits the draft: the connection becomes a stored row (and the running
   * engine when it is new — it was made to be used), and the dialog closes.
   * A name the picker can show is required, so a blank one is rejected rather
   * than rendered as an unnamed engine.
   */
  fun saveDraft() {
    val draft = _draft.value ?: return
    require(draft.label.isNotBlank()) { "an engine needs a label" }
    val isNew = engineRepository.getInstance(draft.id) == null
    engineRepository.addOrUpdate(draft)
    if (isNew) selectEngine(EngineRef.Instance(draft.id))
    cancelDraft()
  }

  /**
   * Deletes the instance with [id]; when it was the selected engine, the
   * selection falls back to the built-in `Offline` (guaranteed by
   * [EngineRepository.delete]).
   */
  fun deleteInstance(id: String) {
    engineRepository.delete(id)
  }
}
