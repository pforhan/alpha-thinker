package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.clients.litert.LiteRTClientConfig
import ai.koog.prompt.executor.clients.litert.LiteRTLLMClient
import ai.koog.prompt.executor.clients.litert.LiteRTLLMProvider
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

  actual fun createStreamingBackend(
    fileName: String,
    directory: String,
    contextTokens: Int,
  ): LocalStreamingBackend? = LiteRTLocalStreamingBackend(
    modelPath = "$directory/$fileName",
    contextTokens = contextTokens,
  )

  actual fun fileExists(path: String): Boolean = File(path).isFile

  actual fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }
    .getOrDefault(false) || !File(path).exists()
}

/**
 * Streams a LiteRT reply through [LocalStreamingBackend].
 *
 * The Koog client deliberately exposes only a completed message, so the eval
 * harness talks to the runtime directly here. Each request gets a fresh
 * conversation: the eval prompts are independent cells, and reusing one would
 * carry the previous answer into the next measurement. The engine, which is the
 * expensive part, stays loaded for the model's whole sweep.
 */
private class LiteRTLocalStreamingBackend(
  private val modelPath: String,
  private val contextTokens: Int,
) : LocalStreamingBackend {
  private val mutex = Mutex()
  private var engine: Engine? = null

  private fun engine(): Engine {
    engine?.let { return it }
    val created = Engine(
      EngineConfig(
        modelPath = modelPath,
        backend = Backend.CPU(),
        maxNumTokens = contextTokens.takeIf { it > 0 },
      ),
    )
    created.initialize()
    engine = created
    return created
  }

  @OptIn(ExperimentalApi::class)
  override suspend fun execute(
    text: String,
    onToken: (String) -> Unit,
  ): LocalStreamingReply = mutex.withLock {
    withContext(Dispatchers.IO) {
      val conversation = engine().createConversation(
        ConversationConfig(
          // The Python harness measures temperature 0, and the row records that
          // value, so the streaming path must actually run greedy rather than
          // inherit the runtime's sampling default.
          samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.0, seed = 0),
        ),
      )
      val reply = StringBuilder()
      try {
        conversation.sendMessageAsync(text).collect { message ->
          val delta = message.textDelta()
          if (delta.isNotEmpty()) {
            reply.append(delta)
            onToken(delta)
          }
        }
        val benchmark = conversation.getBenchmarkInfo()
        LocalStreamingReply(
          text = reply.toString(),
          inputTokens = benchmark.lastPrefillTokenCount.takeIf { it > 0 },
          outputTokens = benchmark.lastDecodeTokenCount.takeIf { it > 0 },
          firstTokenS = benchmark.timeToFirstTokenInSecond.takeIf { it > 0.0 },
          promptEvalS = secondsFor(benchmark.lastPrefillTokenCount, benchmark.lastPrefillTokensPerSecond),
          evalS = secondsFor(benchmark.lastDecodeTokenCount, benchmark.lastDecodeTokensPerSecond),
          genTps = benchmark.lastDecodeTokensPerSecond.takeIf { it > 0.0 },
        )
      } catch (t: Throwable) {
        // A token callback that throws is the harness cutting a loop short.
        // Stop the native generation too, or it keeps burning CPU after the
        // collector is gone.
        runCatching { conversation.cancelProcess() }
        throw t
      } finally {
        runCatching { conversation.close() }
      }
    }
  }

  override fun close() {
    val current = engine ?: return
    engine = null
    runCatching { current.close() }
  }

  private fun Message.textDelta(): String = contents.contents
    .filterIsInstance<Content.Text>()
    .joinToString("") { it.text }

  private fun secondsFor(tokens: Int, tokensPerSecond: Double): Double? =
    (tokens.takeIf { it > 0 }?.toDouble()?.div(tokensPerSecond))?.takeIf { it.isFinite() }
}

