package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.engine.EngineAvailability.Available
import alphainterplanetary.thinker.engine.EngineAvailability.Unavailable
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
  val availability: (EngineRepository) -> EngineAvailability,
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
      availability = { Available },
    ),
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.OnDevice),
      kind = EngineKind.OnDevice,
      provider = null,
      tier = EngineTier.Advanced,
      label = "On device",
      description = "System models (Gemini Nano / Apple Foundation) running locally.",
      availability = { Unavailable("runtime not implemented") },
    ),
    // The free Alpha Interplanetary backend is a placeholder for now.
    // It is a Remote kind but fixed to a specific provider.
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.Remote),
      kind = EngineKind.Remote,
      provider = RemoteProvider.Default,
      tier = EngineTier.Free,
      label = "Alpha Interplanetary",
      description = "The official Alpha Thinker cloud engine.",
      availability = { Unavailable("placeholder") },
    ),
  )

  /**
   * Merges built-ins and user-created instances into a flat list of entries.
   * Instances are mapped to [EngineCatalogEntry] and added to the list.
   */
  fun entries(
    instances: List<EngineInstance>,
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
        availability = { repo ->
          // Per-ref self-validation using the repository as the source of truth
          val current = repo.getInstance(instance.id)
            ?: return@EngineCatalogEntry Unavailable("Instance missing")

          when (current.kind) {
            EngineKind.LocalFile -> {
              // TODO: Implement actual file existence check here.
              // For now, we just check if the path is not blank.
              if (current.localPath.isNullOrBlank()) {
                Unavailable("No local path specified")
              } else {
                Available
              }
            }
            EngineKind.Remote -> {
              if (current.provider == null) {
              // Remotes require a provider.
                Unavailable("Bad configuration")
              } else if (current.provider.requiresApiKey && current.apiKey.isBlank()) {
                Unavailable("API key required")
              } else if (current.baseUrl.isBlank() || current.model.isBlank()) {
                Unavailable("Connection incomplete")
              } else {
                Available
              }
            }

            EngineKind.Offline -> Available
            EngineKind.OnDevice -> TODO()
          }
        },
      )
    }
    return builtins + userEntries
  }
}
