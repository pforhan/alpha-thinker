package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.engine.EngineAvailability.Available
import alphainterplanetary.thinker.engine.EngineAvailability.Unavailable
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
