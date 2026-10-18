package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineAvailability
import alphainterplanetary.thinker.engine.EngineAvailability.NotImplemented
import alphainterplanetary.thinker.engine.EngineAvailability.Unavailable
import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteProvider

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
 * capability, an offline mode) — the presentation supplies that text, so the
 * derivation carries no user-facing copy beyond the values themselves.
 */
data class CapabilityStatus(
  val capability: EngineCapability,
  val state: CapabilityState,
  val detail: String,
)

/**
 * The app-wide engine status: what the selected [EngineKind] *will do*, derived
 * from the ref and the remote connection settings — never from a probe of the
 * device, and never from a toggle, so it cannot disagree with
 * [alphainterplanetary.thinker.engine.EngineRegistry].
 *
 * The header cluster, the flyout's rows, and the Status sheet all read this one
 * derivation instead of each re-deriving the mode with its own `when`.
 */
data class EngineStatus(
  val mode: EngineKind,
  val slots: List<CapabilityStatus>,
) {
  /** The slot for [capability]; every mode reports all three, so always found. */
  fun slot(capability: EngineCapability): CapabilityStatus =
    slots.first { it.capability == capability }

  /**
   * Whether any slot is selected-but-unconfigured: the one definition of "the
   * user has not finished setup", read by the compact form's status dot (see
   * `dotColor`). A plain muted pill would read as "working as intended" when the
   * user has not finished setup, so the dot has to be able to ask.
   */
  val needsAttention: Boolean
    get() = slots.any { it.state == CapabilityState.Incomplete }
}

/**
 * Derives the status the chrome displays for [ref].
 *
 * [instance] is the configuration for [EngineRef.Instance] refs. The base URL is
 * shown as configured rather than re-normalized here: the `/v1` strip in
 * [alphainterplanetary.thinker.engine.RemoteKoogPlanningBackend] is the one rule for
 * that, and a second copy here would be free to drift from it. A provider whose
 * endpoint is not user-configurable ([RemoteProvider.configurableBaseUrl] =
 * false) is always "configured" at its fixed origin, so it is shown as active
 * even when the stored URL field is empty or stale from a previous provider.
 */
fun engineStatus(
  ref: EngineRef,
  instance: EngineInstance? = null,
  availability: EngineAvailability,
): EngineStatus {
  val mode = when (ref) {
    is EngineRef.Builtin -> ref.kind
    is EngineRef.Instance -> instance?.kind ?: EngineKind.Remote
  }

  val url = instance?.baseUrl?.trim() ?: ""
  val model = instance?.model?.trim() ?: ""
  val provider = instance?.provider ?: RemoteProvider.Default
  val apiKey = instance?.apiKey ?: ""

  // null means "the mode needs this and the app does not have it".
  val networkDetail: String? = when (mode) {
    EngineKind.Offline -> null
    EngineKind.Remote -> when {
      // A provider that requires a key has nothing to send until one is set.
      provider.requiresApiKey && apiKey.isBlank() -> null
      // A fixed origin is always reachable-looking; the model and key carry the
      // incompleteness. Anthropic's endpoint shows its own origin.
      !provider.configurableBaseUrl -> provider.defaultBaseUrl
      else -> url.ifBlank { null }
    }
    // No endpoint is involved at all: only Remote egresses.
    EngineKind.OnDevice, EngineKind.LocalFile -> null
  }
  val llmDetail: String? = when (mode) {
    EngineKind.Offline -> null
    EngineKind.Remote -> model.ifBlank { null }
    // The runtime names its own model; the mode is how the user selected it.
    EngineKind.OnDevice, EngineKind.LocalFile ->
      if (availability is Unavailable || availability is NotImplemented) null else mode.label
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
        // Even if detail is present, the engine might be unavailable (e.g. runtime missing)
        // or not implemented yet.
        availability is Unavailable || availability is NotImplemented -> CapabilityState.Incomplete
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
