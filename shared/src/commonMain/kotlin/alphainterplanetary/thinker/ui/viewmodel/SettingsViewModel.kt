package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.engine.RemoteModelCatalog
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.repository.SettingsRepository
import alphainterplanetary.thinker.ui.theme.PhaseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  private val modelCatalog: RemoteModelCatalog,
  private val scope: CoroutineScope,
) {
  /** The selected phase-color theme; changes apply immediately and persist. */
  val phaseTheme: StateFlow<PhaseTheme> = settingsRepository.phaseTheme

  fun selectPhaseTheme(theme: PhaseTheme) {
    settingsRepository.setPhaseTheme(theme)
  }

  /** The selected planning backend; only available modes can be chosen. */
  val engineMode: StateFlow<EngineMode> = settingsRepository.engineMode

  fun selectEngineMode(mode: EngineMode) {
    settingsRepository.setEngineMode(mode)
  }

  /** The remote protocol the Remote backend speaks; switching re-seeds its fields. */
  val remoteLlmProvider: StateFlow<RemoteProvider> = settingsRepository.remoteLlmProvider

  fun selectRemoteProvider(provider: RemoteProvider) {
    if (provider == settingsRepository.remoteLlmProvider.value) return
    settingsRepository.setRemoteLlmProvider(provider)
    // A model list is only meaningful for the provider it was read from, so a
    // switch discards it rather than leaving the old server's models on screen.
    _remoteModels.value = RemoteModelListState.Idle
  }

  /** The endpoint the Remote backend connects to. */
  val remoteLlmBaseUrl: StateFlow<String> = settingsRepository.remoteLlmBaseUrl

  fun setRemoteLlmBaseUrl(url: String) {
    settingsRepository.setRemoteLlmBaseUrl(url)
  }

  /** The API key for the remote endpoint (empty for providers that need none). */
  val remoteLlmApiKey: StateFlow<String> = settingsRepository.remoteLlmApiKey

  fun setRemoteLlmApiKey(key: String) {
    settingsRepository.setRemoteLlmApiKey(key)
  }

  /** The model name the remote endpoint serves. */
  val remoteLlmModel: StateFlow<String> = settingsRepository.remoteLlmModel

  fun setRemoteLlmModel(model: String) {
    settingsRepository.setRemoteLlmModel(model)
  }

  /**
   * The context window declared for [remoteLlmModel] — the number a model named
   * in a settings field cannot supply for itself, and that the planning-context
   * budget is a share of. Filled in from the provider's catalogue when one can
   * be read ([selectRemoteModel]).
   */
  val remoteLlmContextTokens: StateFlow<Int> = settingsRepository.remoteLlmContextTokens

  fun setRemoteLlmContextTokens(tokens: Int) {
    settingsRepository.setRemoteLlmContextTokens(tokens)
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
    val provider = settingsRepository.remoteLlmProvider.value
    val baseUrl = settingsRepository.remoteLlmBaseUrl.value
    val apiKey = settingsRepository.remoteLlmApiKey.value
    _remoteModels.value = RemoteModelListState.Loading
    scope.launch {
      _remoteModels.value = try {
        RemoteModelListState.Loaded(modelCatalog.list(provider, baseUrl, apiKey))
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
    settingsRepository.setRemoteLlmModel(id)
    if (contextLength != null) {
      settingsRepository.setRemoteLlmContextTokens(contextLength)
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
