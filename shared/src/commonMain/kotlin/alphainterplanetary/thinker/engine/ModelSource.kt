package alphainterplanetary.thinker.engine

/**
 * Where a model was learned about — the catalogue that answered when the app
 * asked what a provider serves.
 *
 * The source is not bookkeeping: it decides what the app is allowed to know
 * about a model. A native Ollama server reports a model's size, quantization,
 * and capabilities, while the generic `/v1` wire format carries an id and
 * little else, so a field the app cannot fill is a fact about the source
 * rather than a gap in the listing. It also tells the curation rules
 * (MODEL-CATALOG-PLAN.md stage 2) which evidence applies to a model.
 */
enum class ModelSource {
  /** A native Ollama server, asked over its management API. */
  Ollama,

  /** A generic `/v1` server: OpenAI, OpenRouter, Groq, vLLM, LM Studio, … */
  OpenAiCompatible,

  /** Anthropic's Claude models, from Koog's bundled catalogue. */
  Anthropic,

  /** A model downloaded to the device and run in-process (LiteRT-LM). */
  LiteRt,
}
