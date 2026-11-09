package alphainterplanetary.thinker.downloads

import kotlinx.coroutines.flow.Flow

/**
 * Client for streaming model pulls from a native Ollama instance.
 */
interface OllamaPullClient {
  fun pull(
    baseUrl: String,
    modelId: String,
  ): Flow<OllamaPullEvent>
}
