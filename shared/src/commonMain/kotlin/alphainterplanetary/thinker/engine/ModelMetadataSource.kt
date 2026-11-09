package alphainterplanetary.thinker.engine

/**
 * Where a model's metadata was learned about — the catalogue/path that answered
 * when the app asked what a provider serves.
 *
 * This is **metadata provenance**, not the connection provider
 * ([RemoteProvider]). A [RemoteProvider] describes how to connect (protocol,
 * defaults, key requirements); a [ModelMetadataSource] describes what that
 * source can truthfully report about a model. A native Ollama server reports a
 * model's size, quantization, and capabilities, while the generic `/v1` wire
 * format carries an id and little else, so a field the app cannot fill is a
 * fact about the source rather than a gap in the listing. It also tells the
 * curation rules (MODEL-CATALOG-PLAN.md stage 2) which evidence applies to a
 * model.
 *
 * Today each [RemoteProvider] maps to one metadata source (see
 * [RemoteProvider.metadataSource]); the concepts stay separate so provider
 * wiring and metadata provenance cannot drift into one assumption.
 */
enum class ModelMetadataSource {
  /** A native Ollama server, asked over its management API. */
  Ollama,

  /** A generic `/v1` server: OpenAI, OpenRouter, Groq, vLLM, LM Studio, … */
  OpenAiCompatible,

  /** Anthropic's Claude models, from Koog's bundled catalogue. */
  Anthropic,
}
