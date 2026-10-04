package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineCapability

/**
 * The chrome's user-facing copy for an [EngineStatus]. Kept out of
 * [EngineStatus] itself: the derivation is pure data so it can be tested
 * without asserting on English, while the header cluster, the flyout rows, and
 * the Status sheet all need to word the same state the same way, so the wording
 * lives in one place.
 */
internal fun EngineCapability.displayName(): String =
  when (this) {
    EngineCapability.Network -> "Network"
    EngineCapability.Llm -> "LLM"
    EngineCapability.Tools -> "Tools"
  }

/**
 * How a slot's state reads in a sentence: the one place the three states are
 * worded. Both the sentence a screen reader announces and the value a column
 * shows when there is nothing else to show are drawn from it, so a state is never
 * called one thing in the accessibility text and another in the row beside it.
 */
internal fun CapabilityState.readout(): String =
  when (this) {
    CapabilityState.Active -> "in use"
    CapabilityState.Incomplete -> "needs setup"
    CapabilityState.Unused -> "not used"
  }

/** [readout] as a value-column entry, where it opens the string. */
private fun CapabilityState.valueReadout(): String =
  readout().replaceFirstChar { it.uppercase() }

/**
 * The value to show for a slot: its detail, or the state's own words when the
 * mode has nothing to name (a Lite mode, or Tools before the lookup work).
 */
internal fun CapabilityStatus.displayDetail(): String {
  if (detail.isNotBlank()) return detail
  // An active slot with no value is fully described by its pill, which shows the
  // "in use" fill and check; a word here would only repeat it.
  return if (state == CapabilityState.Active) "" else state.valueReadout()
}

/** One slot as a sentence, for a screen reader and for the compact form. */
internal fun CapabilityStatus.sentence(): String =
  "${capability.displayName()}: ${state.readout()}" +
    if (detail.isBlank()) "" else " — $detail"

/** The whole status as one sentence, describing what the app will do. */
internal fun EngineStatus.summary(): String =
  "Engine status: ${mode.label}. " + slots.joinToString(" ") { it.sentence() }
