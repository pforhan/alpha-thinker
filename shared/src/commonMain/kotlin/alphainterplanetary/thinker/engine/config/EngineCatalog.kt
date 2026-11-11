package alphainterplanetary.thinker.engine.config

import alphainterplanetary.thinker.engine.config.EngineAvailability.Available
import alphainterplanetary.thinker.engine.config.EngineAvailability.Unavailable

object EngineCatalog {
  /**
   * The fixed connection for the free, built-in Alpha Interplanetary backend: a
   * rate-limited Ollama endpoint offered without user configuration.
   */
  val alphaInterplanetaryConnection = RemoteConnection(
    provider = RemoteProvider.Ollama,
    baseUrl = "https://alpha.tail2dd774.ts.net/",
    apiKey = "",
    model = "llama3.2:latest",
    contextLength = 32_000,
  )

  val builtins = listOf(
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.Offline),
      kind = EngineKind.Offline,
      provider = null,
      tier = EnginePickerTier.Offline,
      label = "Offline",
      description = "Built-in question library. Runs entirely on this device — no network, no language model.",
      availability = { Available },
    ),
    // A free, uneditable remote: the Alpha Interplanetary Ollama backend.
    EngineCatalogEntry(
      ref = EngineRef.Builtin(EngineKind.Remote),
      kind = EngineKind.Remote,
      provider = RemoteProvider.Ollama,
      tier = EnginePickerTier.Free,
      label = "Alpha Interplanetary",
      description = "The official Alpha Interplanetary cloud engine.",
      availability = { Available },
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
        tier = EnginePickerTier.Advanced,
        label = instance.label,
        description = instance.kind.description,
        availability = { repo ->
          // Per-ref self-validation using the repository as the source of truth
          val current = repo.getInstance(instance.id)
            ?: return@EngineCatalogEntry Unavailable("Instance missing")

          when (current.kind) {
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
          }
        },
      )
    }
    return builtins + userEntries
  }

  /**
   * The picker's view of [entries]: one group per [EnginePickerTier] in display
   * order — `Offline` and `Free` (the preferred cards) then `Advanced` (the
   * disclosure) — each group ordered by recency, most recently used first,
   * with never-used engines keeping the list's creation order behind them.
   * Empty tiers are dropped.
   *
   * Pure over its inputs, so the picker renders from the merge alone and the
   * ordering rule has one home (ENGINE-CONFIG-PLAN.md stage 3c).
   */
  fun groupedByTier(
    instances: List<EngineInstance>,
    lastUsedAt: Map<EngineRef, Long> = emptyMap(),
  ): List<Pair<EnginePickerTier, List<EngineCatalogEntry>>> = EnginePickerTier.entries.mapNotNull { tier ->
    val group = entries(instances)
      .filter { it.tier == tier }
      .sortedByDescending { lastUsedAt[it.ref] ?: 0L }
    if (group.isEmpty()) null else tier to group
  }
}
