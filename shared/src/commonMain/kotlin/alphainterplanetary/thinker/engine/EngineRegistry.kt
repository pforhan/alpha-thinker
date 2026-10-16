package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.repository.EngineRepository
import me.tatarka.inject.annotations.Inject

/**
 * Builds and caches a [PlanningEngine] per [EngineRef].
 *
 * An engine's lifecycle is tied to the registry: its inner Koog client is
 * closed when the registry is cleared or when a specific engine is invalidated.
 */
class EngineRegistry @Inject constructor(
  private val repository: EngineRepository,
) {
  private val cache = mutableMapOf<EngineRef, PlanningEngine>()

  /** The [PlanningEngine] tied to this ref. */
  fun getEngine(ref: EngineRef): PlanningEngine {
    return cache.getOrPut(ref) {
      createEngine(ref)
    }
  }

  private fun createEngine(ref: EngineRef): PlanningEngine = when (ref) {
    is EngineRef.Builtin -> when (ref.kind) {
      EngineKind.Offline -> OfflinePlanningEngine()
      else -> throw IllegalStateException("Built-in engine ${ref.kind} not yet implemented")
    }
    is EngineRef.Instance -> {
      val instance = repository.getInstance(ref.id)
        ?: throw IllegalStateException("Engine instance ${ref.id} not found")

      if (instance.kind == EngineKind.Remote) {
        val connection = RemoteConnection(
          provider = instance.provider ?: RemoteProvider.Default,
          baseUrl = instance.baseUrl,
          apiKey = instance.apiKey,
          model = instance.model,
          contextLength = instance.contextTokens,
        )
        KoogPlanningEngine(RemoteKoogPlanningBackend(connection))
      } else {
        throw IllegalStateException("Engine instance kind ${instance.kind} not yet implemented")
      }
    }
  }

  /**
   * Use when an instance's configuration has changed to force a rebuild
   * of its associated engine on the next request.
   */
  fun invalidate(ref: EngineRef) {
    cache.remove(ref)
  }
}
