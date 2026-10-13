package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.di.AppScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject

/**
 * Says that a project was written, without saying who wrote it.
 *
 * A screen showing projects can be on screen when something other than itself
 * writes — for example, a tool (like sample generation or a project simulation)
 * writes projects while the list or a detail view is open. The screen did not
 * ask for the change and has no way to learn of it, so it reads a stale project
 * until something makes it reload. This bus is that something: a writer
 * announces, and every reader re-reads.
 *
 * The point is the direction of the dependency. A reader waits on the *event*,
 * never on the writer, so a second writer (or a tool that does not exist yet)
 * needs no new ad-hoc signal — it announces on the same bus. Writes are rare
 * and deliberate (tools populating or simulating projects), so a reader that
 * reloads unconditionally is cheaper than one that tries to work out whether it
 * was affected.
 */
@AppScope
class ProjectWrites @Inject constructor() {

  // extraBufferCapacity lets a non-suspending announce() succeed when a reader
  // is momentarily behind; there is no replay, so a bus that does not fire on
  // subscribe cannot race a screen's own first load.
  private val _writes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** Fires once per announced write; a reader re-reads on every emission. */
  val writes: Flow<Unit> = _writes.asSharedFlow()

  /** Announces a completed write. Does not suspend, so a writer need not be a coroutine. */
  fun announce() {
    _writes.tryEmit(Unit)
  }
}
