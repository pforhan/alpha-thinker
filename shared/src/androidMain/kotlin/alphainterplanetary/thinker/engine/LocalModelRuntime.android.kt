package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.clients.litert.LiteRTClientConfig
import ai.koog.prompt.executor.clients.litert.LiteRTLLMClient
import ai.koog.prompt.executor.clients.litert.LiteRTLLMProvider
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import java.io.File

/**
 * Android is the one target with an in-process runtime: Koog publishes a LiteRT
 * client only for Android, backed by `litertlm-android`.
 */
actual object LocalModelRuntime {
  actual val inferenceSupported: Boolean = true
  actual val downloadsSupported: Boolean = true

  actual fun createBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): KoogPlanningBackend {
    // LiteRT resolves the model as "$modelsPath/${model.id}", so the id is the
    // file name and the path is its parent directory.
    val model = LLModel(
      provider = LiteRTLLMProvider,
      id = fileName,
      capabilities = listOf(LLMCapability.Temperature, LLMCapability.Completion),
      contextLength = contextTokens.toLong(),
      maxOutputTokens = 1_024L,
    )
    val client = LiteRTLLMClient(
      LiteRTClientConfig(
        defaultModel = model,
        modelsPath = directory,
      ),
    )
    return LocalKoogPlanningBackend(
      executor = MultiLLMPromptExecutor(client),
      model = model,
      onClose = client::close,
    )
  }

  actual fun fileExists(path: String): Boolean = File(path).isFile

  actual fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }
    .getOrDefault(false) || !File(path).exists()
}
