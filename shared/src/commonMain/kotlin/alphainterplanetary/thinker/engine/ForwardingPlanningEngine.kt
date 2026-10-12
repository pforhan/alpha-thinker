package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogSource

/**
 * The shared base for [PlanningEngine] decorators: it forwards the three
 * self-describing facts — [source], [contextWindowTokens], and [canSummarize] —
 * to the wrapped [delegate], which is the only answer any of them can have. A
 * decorator observes or throttles an engine; it never changes which producer
 * will run, how large that producer's window is, or what it can do, so it must
 * never spell those out itself. [source] in particular drives the activity
 * log's attribution, and a decorator answering it on its own would file its
 * rows under the wrong producer.
 *
 * The interaction methods are deliberately *not* forwarded: a decorator's whole
 * job is to decide what to do around them, so the compiler should make each
 * decorator answer for every interaction rather than let a new one slip through
 * undecorated.
 */
abstract class ForwardingPlanningEngine(
  protected val delegate: PlanningEngine,
) : PlanningEngine {

  override val source: LogSource
    get() = delegate.source

  override val contextWindowTokens: Int?
    get() = delegate.contextWindowTokens

  override val canSummarize: Boolean
    get() = delegate.canSummarize
}
