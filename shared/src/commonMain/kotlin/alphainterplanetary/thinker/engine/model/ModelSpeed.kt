package alphainterplanetary.thinker.engine.model

/**
 * Speed bands derived from average suite time on the eval harness.
 * Measured on device class in the evals; relative values matter across devices.
 */
enum class ModelSpeed {
  Fast,
  Fine,
  Slow,
  Unknown,
}
