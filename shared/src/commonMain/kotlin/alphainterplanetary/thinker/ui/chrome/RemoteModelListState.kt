package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.config.RemoteProvider
import alphainterplanetary.thinker.engine.model.ModelBrowserEntry
import alphainterplanetary.thinker.engine.model.ModelBrowserMerge
import alphainterplanetary.thinker.engine.model.RemoteModelInfo

/**
 * What the model browse affordance in the connection editor is showing. A sealed
 * interface rather than a list plus a flag, so "loading" and "found nothing"
 * cannot be confused — an empty Ollama server is a real answer, not a spinner.
 */
sealed interface RemoteModelListState {
  /** Nothing requested yet; no list is shown. */
  data object Idle : RemoteModelListState

  /** A request is in flight; the browse control shows a spinner. */
  data object Loading : RemoteModelListState

  /** The provider's models, possibly empty. */
  data class Loaded(val models: List<RemoteModelInfo>) : RemoteModelListState {
    /**
     * The installed [models] plus curated Ollama models that can still be
     * pulled. Only meaningful once a list has loaded: pull candidates are the
     * difference against what the server reports, so the UI does not offer them
     * before a successful browse.
     */
    fun browserEntriesWithPulls(provider: RemoteProvider): List<ModelBrowserEntry> =
      ModelBrowserMerge.merge(models, provider)
  }

  /** The probe failed; [message] is shown in place of the list. */
  data class Failed(val message: String) : RemoteModelListState
}
