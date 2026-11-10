package alphainterplanetary.thinker.engine.model

/**
 * One model a remote provider offers: the id to ask for, the window it
 * serves, and whatever else the provider could tell us about it.
 *
 * [id] is the canonical reference — what is sent to the provider — and
 * [displayName] is what the browser shows, which differs from it only when the
 * id is an alias: `llama3.2:latest` is really `llama3.2:3b`, and the browser
 * shows the resolved name while the id stays what the provider is asked for.
 *
 * The rest is the provider's own metadata, left null where the provider has no
 * way to report it. The generic `/v1` wire format carries an id and little
 * else, so a null there is a fact about the source rather than a gap in the
 * listing — which is why [source] is part of the model rather than a guess
 * made from the fields that happen to be filled.
 */
data class RemoteModelInfo(
  val id: String,
  val source: ModelMetadataSource,
  val displayName: String = id,
  val contextLength: Int? = null,
  val maxOutputTokens: Int? = null,
  /** The parameter size in billions, rounded: "3.2B" is 3. */
  val parameterSizeB: Int? = null,
  /** The parameter size as the source declares it ("3.2B", "873.44M"). */
  val sizeLabel: String? = null,
  /** The model's exact parameter count, where the source reports one. */
  val parameterCount: Long? = null,
  /** The on-disk size of the weights, in bytes, where the source reports one. */
  val sizeBytes: Long? = null,
  /** The model's manifest digest (Ollama SHA), where the source reports one. */
  val digest: String? = null,
  val quantization: String? = null,
  val format: String? = null,
  val capabilities: Set<String> = emptySet(),
)
