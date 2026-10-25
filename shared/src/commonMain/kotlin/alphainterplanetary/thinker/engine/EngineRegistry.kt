package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.repository.EngineRepository
import me.tatarka.inject.annotations.Inject

/**
 * Builds and caches a [PlanningEngine] per [EngineRef].
 *
 * An engine's lifecycle is tied to the registry: [invalidate] closes the engine
 * it evicts, so the next request builds a fresh one and the released engine does
 * not hold its client open for the life of the app.
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
   * of its associated engine on the next request. The evicted engine is closed
   * here rather than dropped, because it owns a client and dropping the
   * reference is otherwise the only thing that would happen to it.
   *
   * This assumes nothing is still using the evicted engine. A task already
   * enqueued holds the engine it was frozen with (see `ProjectRepository`), so
   * invalidating while one is in flight closes the client out from under it:
   * treat this as "the connection changed and no task is waiting on the old one".
   */
  fun invalidate(ref: EngineRef) {
    cache.remove(ref)?.close()
  }
}
