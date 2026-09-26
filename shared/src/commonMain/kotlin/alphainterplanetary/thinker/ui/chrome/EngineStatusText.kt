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

/** How a slot's state reads in a sentence. */
internal fun CapabilityState.readout(): String =
  when (this) {
    CapabilityState.Active -> "in use"
    CapabilityState.Incomplete -> "needs setup"
    CapabilityState.Unused -> "not used"
  }

/**
 * The value to show for a slot: its detail, or the state's own words when the
 * mode has nothing to name (a Lite mode, or Tools before the lookup work).
 */
internal fun CapabilityStatus.displayDetail(): String =
  detail.ifBlank {
    when (state) {
      CapabilityState.Active -> ""
      CapabilityState.Incomplete -> "Not set up"
      CapabilityState.Unused -> "Not used"
    }
  }

/** One slot as a sentence, for a screen reader and for the compact form. */
internal fun CapabilityStatus.sentence(): String =
  "${capability.displayName()}: ${state.readout()}" +
    if (detail.isBlank()) "" else " — $detail"

/** The whole status as one sentence, describing what the app will do. */
internal fun EngineStatus.summary(): String =
  "Engine status: ${mode.label}. " + slots.joinToString(" ") { it.sentence() }
