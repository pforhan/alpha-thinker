package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteConnection
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  private val engineRepository: EngineRepository,
  private val modelCatalog: RemoteModelCatalog,
  private val scope: CoroutineScope,
) {
  /** The selected phase-color theme; changes apply immediately and persist. */
  val phaseTheme: StateFlow<PhaseTheme> = settingsRepository.phaseTheme

  fun selectPhaseTheme(theme: PhaseTheme) {
    settingsRepository.setPhaseTheme(theme)
  }

  /** The selected planning backend; only available modes can be chosen. */
  val selectedRef: StateFlow<EngineRef> = engineRepository.selected

  fun selectEngine(ref: EngineRef) {
    engineRepository.select(ref)
  }

  /**
   * The selected instance's Remote connection fields, or the blank placeholder
   * shown while a builtin is selected. The settings sheet collects this one
   * flow and reads each field off the [RemoteConnection] rather than wiring a
   * projection (and its default) per field.
   *
   * It starts eagerly so the value is already current when the sheet opens,
   * instead of replaying the placeholder while a lazy collector catches up. The
   * ViewModel is app-scoped and the upstream is a pair of state flows, so the
   * always-on collector costs nothing to keep.
   * TODO revisit later to see if we can use WhileSubscribed(5_000) used elsewhere
   */
  val remoteConnection: StateFlow<RemoteConnection> = engineRepository.selectedInstance
    .map { it.toRemoteConnection() }
    .stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue = BlankRemoteConnection,
    )

  /**
   * Switches the Remote protocol and re-seeds the fields the previous provider
   * supplied, so one provider's endpoint, starter model, and window do not ride
   * across into a connection that cannot use them (ENGINE-CONFIG-PLAN.md 2a3).
   *
   * Each field is re-seeded only while it still holds the previous provider's
   * starter value, so a hand-edited endpoint, model, or window is kept. The
   * window also counts as starter while the model is, because it is the number
   * that starter resolves to — the code default, or the window a metadata lookup
   * refined it to ([seedProviderWindow]).
   */
  fun selectRemoteProvider(provider: RemoteProvider) {
    val ref = engineRepository.selected.value as? EngineRef.Instance ?: return
    val current = engineRepository.getInstance(ref.id) ?: return
    val previous = current.provider ?: RemoteProvider.Default
    val reseedEndpoint = current.baseUrl == previous.defaultBaseUrl
    val reseedModel = current.model == previous.defaultModel
    val reseedWindow = reseedModel || current.contextTokens == previous.defaultContextTokens

    val switched = current.copy(
      provider = provider,
      baseUrl = if (reseedEndpoint) provider.defaultBaseUrl else current.baseUrl,
      model = if (reseedModel) provider.defaultModel else current.model,
      contextTokens = if (reseedWindow) provider.defaultContextTokens else current.contextTokens,
    )
    engineRepository.addOrUpdate(switched)
    // A model list is only meaningful for the provider it was read from, so a
    // switch discards it rather than leaving the old server's models on screen.
    _remoteModels.value = RemoteModelListState.Idle
    if (reseedWindow) seedProviderWindow(provider, switched.baseUrl, switched.model)
  }

  /**
   * Replaces the window [selectRemoteProvider] just seeded with the one the
   * provider reports for [model], when it can report one. The provider need not
   * answer — an OpenAI-compatible endpoint exposes no per-model window and a
   * local Ollama server may be down — in which case the seeded default stands.
   * The lookup lands asynchronously, so it is applied only while the instance
   * still carries that seeded default; a window typed in the meantime is never
   * overwritten.
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
      engineRepository.updateSelectedInstance { live ->
        if (live.provider == provider &&
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

  /** Sets the endpoint the Remote backend connects to. */
  fun setRemoteBaseUrl(url: String) {
    engineRepository.updateSelectedInstance { it.copy(baseUrl = url) }
  }

  /** Sets the API key for the remote endpoint (empty for providers that need none). */
  fun setRemoteApiKey(key: String) {
    engineRepository.updateSelectedInstance { it.copy(apiKey = key) }
  }

  /** Sets the model name the remote endpoint serves. */
  fun setRemoteModel(model: String) {
    engineRepository.updateSelectedInstance { it.copy(model = model) }
  }

  /**
   * Sets the context window declared for the model — the number a model named
   * in a settings field cannot supply for itself, and that the planning-context
   * budget is a share of. Filled in from the provider's catalogue when one can
   * be read ([selectRemoteModel]).
   */
  fun setRemoteContextTokens(tokens: Int) {
    require(tokens > 0) { "a model context window must be positive: $tokens" }
    engineRepository.updateSelectedInstance { it.copy(contextTokens = tokens) }
  }

  /** What the model browser is showing, from the last [refreshRemoteModels]. */
  private val _remoteModels = MutableStateFlow<RemoteModelListState>(RemoteModelListState.Idle)
  val remoteModels: StateFlow<RemoteModelListState> = _remoteModels.asStateFlow()

  /**
   * Reads the selected provider's model list for the current endpoint. Instant
   * for a provider with a bundled catalogue (Anthropic); a network round trip
   * for one that has to be asked (Ollama's management API, the generic
   * OpenAI-compatible `GET /v1/models`). A failure is captured in the state
   * rather than thrown, because the browser is an affordance beside a field the
   * user can still type into.
   */
  fun refreshRemoteModels() {
    val ref = engineRepository.selected.value as? EngineRef.Instance ?: return
    val instance = engineRepository.getInstance(ref.id) ?: return
    val provider = instance.provider ?: return
    _remoteModels.value = RemoteModelListState.Loading
    scope.launch {
      _remoteModels.value = try {
        RemoteModelListState.Loaded(modelCatalog.list(provider, instance.baseUrl, instance.apiKey))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        RemoteModelListState.Failed(e.message ?: e.toString())
      }
    }
  }

  /**
   * Picks [id] and, when its window is known, adopts it as the declared context.
   * The window is the reason to browse at all: a catalogue can say how big the
   * model really is — when it can, the budget stops guessing. An
   * OpenAI-compatible listing often cannot (its wire format carries no window),
   * in which case [selectRemoteModel] still fills in the id.
   */
  fun selectRemoteModel(id: String, contextLength: Int?) {
    engineRepository.updateSelectedInstance { current ->
      if (contextLength != null) {
        current.copy(model = id, contextTokens = contextLength)
      } else {
        current.copy(model = id)
      }
    }
  }

  /**
   * The newest failure activity already raised for the user, and the write that
   * records one. Hoisted here with the rest of the settings because the chrome
   * reaches persistence only through this ViewModel; nothing in the settings UI
   * reads it, and it is listed with the others rather than promoted to a
   * top-level screen concern precisely because it is not one.
   */
  val announcedFailureActivityId: StateFlow<String?> = settingsRepository.announcedFailureActivityId

  fun markFailureAnnounced(activityId: String) {
    settingsRepository.markFailureAnnounced(activityId)
  }
}

/**
 * The connection the Remote card displays while a builtin — or nothing — is
 * selected: the app's default protocol, an unset endpoint and model, no key,
 * and no declared window. It is the one place those fallbacks are defined.
 */
private val BlankRemoteConnection = RemoteConnection(
  provider = RemoteProvider.Default,
  baseUrl = "",
  apiKey = "",
  model = "",
  contextLength = 0,
)

/** Projects the selected instance (or its absence) into the Remote card's state. */
private fun EngineInstance?.toRemoteConnection(): RemoteConnection =
  if (this == null) {
    BlankRemoteConnection
  } else {
    RemoteConnection(
      provider = provider ?: RemoteProvider.Default,
      baseUrl = baseUrl,
      apiKey = apiKey,
      model = model,
      contextLength = contextTokens,
    )
  }
