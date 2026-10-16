package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.repository.EngineRepository

/**
 * A code-defined built-in entry (Offline, Free/Alpha Interplanetary, On-device).
 */
data class EngineCatalogEntry(
  val ref: EngineRef,
  val kind: EngineKind,
  val provider: RemoteProvider?,
  val tier: EngineTier,
  val label: String,
  val description: String,
  val availability: (EngineRepository) -> Boolean,
)

object EngineCatalog {
  val builtins = listOf(
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.Offline),
      kind = EngineKind.Offline,
      provider = null,
      tier = EngineTier.Offline,
      label = "Offline",
      description = "Built-in question library. Runs entirely on this device — no network, no language model.",
      availability = { true },
    ),
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.OnDevice),
      kind = EngineKind.OnDevice,
      provider = null,
      tier = EngineTier.Advanced,
      label = "On device",
      description = "System models (Gemini Nano / Apple Foundation) running locally.",
      availability = { false }, // Implement real check later when runtime is available
    ),
    // The free Alpha Interplanetary backend is a placeholder for now.
    // It is a Remote kind but fixed to a specific provider.
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.Remote), // Note: if we want this to be uniquely identifiable, we might need a different EngineRef.Builtin key
      kind = EngineKind.Remote,
      provider = RemoteProvider.Default, // Placeholder
      tier = EngineTier.Free,
      label = "Alpha Interplanetary",
      description = "The official Alpha Thinker cloud engine.",
      availability = { false },
    ),
  )

  /**
   * Merges built-ins and user-created instances into a flat list of entries.
   * Instances are mapped to [EngineCatalogEntry] and added to the list.
   */
  fun entries(
    instances: List<EngineInstance>,
    repository: EngineRepository,
  ): List<EngineCatalogEntry> {
    val userEntries = instances.map { instance ->
      EngineCatalogEntry(
        ref = EngineRef.Instance(instance.id),
        kind = instance.kind,
        provider = instance.provider,
        tier = when (instance.kind) {
          EngineKind.Remote -> EngineTier.Advanced
          EngineKind.LocalFile -> EngineTier.Local
          else -> EngineTier.Advanced
        },
        label = instance.label,
        description = instance.kind.description,
        availability = { true }, // Instances are generally available if they exist
      )
    }
    return builtins + userEntries
  }
}
