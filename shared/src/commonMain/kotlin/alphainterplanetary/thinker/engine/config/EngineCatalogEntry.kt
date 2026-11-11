package alphainterplanetary.thinker.engine.config

import alphainterplanetary.thinker.repository.EngineRepository

/**
 * A code-defined built-in entry (Offline, Free/Alpha Interplanetary).
 */
data class EngineCatalogEntry(
  val ref: EngineRef,
  val kind: EngineKind,
  val provider: RemoteProvider?,
  val tier: EnginePickerTier,
  val label: String,
  val description: String,
  val availability: (EngineRepository) -> EngineAvailability,
)
