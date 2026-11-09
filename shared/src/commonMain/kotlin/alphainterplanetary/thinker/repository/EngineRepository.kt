package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.di.AppScope
import alphainterplanetary.thinker.engine.config.EngineCatalog
import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EngineKind
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.engine.config.EngineSelectionState
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Owns the user-created engines and which one is selected. It replaces the flat
 * single-instance remote settings with a list of [EngineInstance]s plus an
 * [EngineSelectionState] per [EngineRef], so several connections can coexist and each
 * keeps its own endpoint, key, model, and window (ENGINE-CONFIG-PLAN.md).
 *
 * A fresh install is seeded with one Remote instance ([EngineInstance.defaultRemote]),
 * so the picker always has something configured; the built-in `Offline` engine
 * stays selected until the user chooses otherwise. Built-in refs are never
 * promoted to instance rows — [lastUsedAt] is what orders them in the picker.
 */
@AppScope
class EngineRepository @Inject constructor(
  private val storage: Storage,
  private val scope: CoroutineScope,
) {
  private val _instances = MutableStateFlow<List<EngineInstance>>(emptyList())

  /** Returns the instance for [id], or null. */
  fun getInstance(id: String): EngineInstance? =
    _instances.value.find { it.id == id }

  /** All user-created instances. */
  val instances: StateFlow<List<EngineInstance>> = _instances.asStateFlow()

  private val _selected = MutableStateFlow<EngineRef>(EngineRef.Builtin(EngineKind.Default))

  /** The instance behind [selected], or null when a builtin is selected. */
  val selectedInstance: Flow<EngineInstance?> = combine(_selected, _instances) { ref, instances ->
    (ref as? EngineRef.Instance)?.let { r -> instances.find { it.id == r.id } }
  }

  /** The engine a task will run on; the built-in `Offline` until chosen. */
  val selected: StateFlow<EngineRef> = _selected.asStateFlow()

  private val _lastUsedAt = MutableStateFlow<Map<EngineRef, Long>>(emptyMap())

  /** When each engine last ran, for ordering the picker. Absent means never used. */
  val lastUsedAt: StateFlow<Map<EngineRef, Long>> = _lastUsedAt.asStateFlow()

  private val _firstRunPending = MutableStateFlow(false)

  /**
   * True on a store that has never recorded an engine choice or run — the
   * first-run picker's trigger (ENGINE-CONFIG-PLAN.md 3d). It starts false and
   * flips on [load], so the picker can only open over loaded truth rather than
   * racing a store it has not read yet.
   */
  val firstRunPending: StateFlow<Boolean> = _firstRunPending.asStateFlow()

  init {
    scope.launch { load() }
  }

  /** Reads the persisted engines and selection; seeds a default Remote on first read. */
  suspend fun load() {
    val instances = readInstances()
    _instances.value = instances
    // A retired kind or deleted instance can still be sitting in
    // `engine_selection_state` (an old "on-device" / "downloaded" key parses as an
    // Instance ref that no longer exists). Keep only rows that name a live builtin
    // or a stored instance, and delete the rest so they cannot resurface.
    val liveKeys = EngineCatalog.builtins.mapTo(mutableSetOf()) { it.ref.storageKey }
    instances.mapTo(liveKeys) { EngineRef.Instance(it.id).storageKey }
    val (states, staleStates) = storage.getEngineSelectionStates()
      .partition { it.ref.storageKey in liveKeys }
    staleStates.forEach { storage.deleteEngineSelectionState(it.ref) }
    _firstRunPending.value = shouldOfferFirstRun(states)
    _selected.value = states.firstOrNull { it.selected }?.ref
      ?: EngineRef.Builtin(EngineKind.Default)
    _lastUsedAt.value = states
      .mapNotNull { state -> state.lastUsedAt?.let { state.ref to it } }
      .toMap()
  }

  /**
   * Records that the first-run picker was shown, so it does not reopen on
   * every launch: the current selection gets its `engine_selection_state` row now,
   * and an explicit choice later simply moves the `selected` flag onto its own
   * row. Dismissing the picker without choosing is therefore final — the
   * default stands and the user is not nagged (Decision 3: the picker is a
   * picker, not an onboarding gate).
   */
  fun markFirstRunSeen() {
    _firstRunPending.value = false
    scope.launch { storage.setSelectedEngine(_selected.value) }
  }

  /** Promotes [ref] to the selection and persists it, clearing every other. */
  fun select(ref: EngineRef) {
    if (_selected.value == ref) return
    _selected.value = ref
    scope.launch { storage.setSelectedEngine(ref) }
  }

  /**
   * Records that [ref] just ran a task at [at] (epoch milliseconds), so the
   * picker can order by recency. The selected flag is written alongside, since
   * a state row is upserted whole.
   */
  fun markUsed(ref: EngineRef, at: Long = now().toEpochMilliseconds()) {
    _lastUsedAt.update { it + (ref to at) }
    scope.launch {
      storage.upsertEngineSelectionState(
        EngineSelectionState(ref = ref, selected = _selected.value == ref, lastUsedAt = at),
      )
    }
  }

  /** Updates the currently selected instance's fields via [transform] and persists. */
  fun updateSelectedInstance(transform: (EngineInstance) -> EngineInstance) {
    val ref = _selected.value as? EngineRef.Instance ?: return
    val current = _instances.value.find { it.id == ref.id } ?: return
    addOrUpdate(transform(current))
  }

  /** Inserts or replaces [instance]; a new id appends, an existing one updates in place. */
  fun addOrUpdate(instance: EngineInstance) {
    _instances.update { current ->
      if (current.any { it.id == instance.id }) {
        current.map { if (it.id == instance.id) instance else it }
      } else {
        current + instance
      }
    }
    scope.launch { storage.upsertEngineInstance(instance) }
  }

  /**
   * Deletes the instance with [id]. When it was the selected engine, the
   * selection falls back to the built-in `Offline` so the app never points at an
   * engine that no longer exists.
   */
  fun delete(id: String) {
    val ref = EngineRef.Instance(id)
    _instances.update { current -> current.filterNot { it.id == id } }
    if (_selected.value == ref) {
      select(EngineRef.Builtin(EngineKind.Default))
    }
    scope.launch { storage.deleteEngineInstance(id) }
  }

  private suspend fun readInstances(): List<EngineInstance> {
    val stored = storage.getEngineInstances()
    if (stored.isNotEmpty()) return stored
    val seeded = EngineInstance.defaultRemote()
    storage.upsertEngineInstance(seeded)
    return listOf(seeded)
  }
}

/**
 * The whole first-run rule: no [EngineSelectionState] row has ever been written, so no
 * engine has been chosen or run. Kept pure so the trigger behind
 * [EngineRepository.firstRunPending] is testable without a repository and
 * cannot drift from the storage it describes (ENGINE-CONFIG-PLAN.md 3d — the
 * trigger is a pure function, with no separate first-run flag).
 */
internal fun shouldOfferFirstRun(states: List<EngineSelectionState>): Boolean = states.isEmpty()
