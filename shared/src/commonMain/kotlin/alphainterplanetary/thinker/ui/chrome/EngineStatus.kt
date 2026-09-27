package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineMode

/** How a header slot is rendering, from the active mode's capabilities alone. */
enum class CapabilityState {
  /** The mode has this capability and the app has what it needs to use it. */
  Active,

  /**
   * The mode has this capability but the app is missing what it needs — a
   * Remote mode with no endpoint or no model. Generation will fail when it is
   * used, so the chrome says so instead of claiming the capability works.
   */
  Incomplete,

  /** The mode does not have this capability; the slot renders muted. */
  Unused,
}

/**
 * One stable header slot's displayed state. [detail] carries the slot's current
 * value (the endpoint, the model) for the Status sheet and the flyout rows, and
 * is empty when the mode's own description already explains the slot (an unused
 * capability, a Lite mode) — the presentation supplies that text, so the
 * derivation carries no user-facing copy beyond the values themselves.
 */
data class CapabilityStatus(
  val capability: EngineCapability,
  val state: CapabilityState,
  val detail: String,
)

/**
 * The app-wide engine status: what the selected [EngineMode] *will do*, derived
 * from the mode and the remote connection settings — never from a probe of the
 * device, and never from a toggle, so it cannot disagree with
 * [alphainterplanetary.thinker.engine.resolveSelectedEngine].
 *
 * The header cluster, the flyout's rows, and the Status sheet all read this one
 * derivation instead of each re-deriving the mode with its own `when`.
 */
data class EngineStatus(
  val mode: EngineMode,
  val slots: List<CapabilityStatus>,
) {
  /** The slot for [capability]; every mode reports all three, so always found. */
  fun slot(capability: EngineCapability): CapabilityStatus =
    slots.first { it.capability == capability }

  /**
   * Whether any slot is selected-but-unconfigured. Drives the compact form's
   * status dot and the Status sheet's attention tint: a plain muted pill would
   * read as "working as intended" when the user has not finished setup.
   */
  val needsAttention: Boolean
    get() = slots.any { it.state == CapabilityState.Incomplete }
}

/**
 * Derives the status the chrome displays for [mode].
 *
 * [remoteBaseUrl] and [remoteModel] are the raw settings values, and only
 * [EngineMode.Remote] reads them. The base URL is shown as configured rather
 * than re-normalized here: the `/v1` strip in
 * [alphainterplanetary.thinker.engine.RemoteKoogPlanningBackend] is the one rule for
 * that, and a second copy here would be free to drift from it.
 */
fun engineStatus(
  mode: EngineMode,
  remoteBaseUrl: String = "",
  remoteModel: String = "",
): EngineStatus {
  val url = remoteBaseUrl.trim()
  val model = remoteModel.trim()

  // null means "the mode needs this and the app does not have it".
  val networkDetail: String? = when (mode) {
    EngineMode.Lite -> null
    EngineMode.Remote -> url.ifBlank { null }
    // No endpoint is involved at all: only Remote egresses.
    EngineMode.OnDevice, EngineMode.Downloaded -> mode.label
  }
  val llmDetail: String? = when (mode) {
    EngineMode.Lite -> null
    EngineMode.Remote -> model.ifBlank { null }
    // The runtime names its own model; the mode is how the user selected it.
    EngineMode.OnDevice, EngineMode.Downloaded -> mode.label
  }

  fun statusFor(capability: EngineCapability): CapabilityStatus {
    val supported = capability in mode.capabilities
    val detail = when (capability) {
      EngineCapability.Network -> networkDetail
      EngineCapability.Llm -> llmDetail
      // No mode reports Tools yet; it arrives with the agentic-lookup work, and
      // until then the slot is stable-and-muted rather than absent.
      EngineCapability.Tools -> null
    }
    return CapabilityStatus(
      capability = capability,
      state = when {
        !supported -> CapabilityState.Unused
        detail == null -> CapabilityState.Incomplete
        else -> CapabilityState.Active
      },
      detail = detail.orEmpty(),
    )
  }

  return EngineStatus(
    mode = mode,
    slots = EngineCapability.entries.map(::statusFor),
  )
}
