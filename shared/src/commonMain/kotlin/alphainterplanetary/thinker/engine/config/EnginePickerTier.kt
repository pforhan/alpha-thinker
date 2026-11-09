package alphainterplanetary.thinker.engine.config

/**
 * Where an engine appears in the picker. This is the engine-picker grouping
 * (ENGINE-CONFIG-PLAN.md stage 3), not a model-curation tier (see
 * [ModelPresentationTier]): `Offline` and `Free` are the preferred cards up
 * top, and every user-configured connection lives under the `Advanced`
 * disclosure so the ever-growing list of endpoints never crowds the common
 * choice.
 *
 * The [label] and [tagline] are the tier's user-facing copy on those cards, so
 * the picker renders a preferred engine from the tier alone.
 */
enum class EnginePickerTier(
  /** The user-facing name: the preferred card's title, the Advanced section's header. */
  val label: String,
  /** One line under the name on a preferred card; null for tiers shown as a disclosure. */
  val tagline: String?,
) {
  /** The built-in question library, always available. */
  Offline(
    label = "Offline",
    tagline = "Built-in question library. No network, no language model.",
  ),

  /** The free Alpha Interplanetary backend (a disabled placeholder for now). */
  Free(
    label = "Free",
    tagline = "Alpha Interplanetary's cloud engine. Powered by a known-solid model.",
  ),

  /** Configured Remote connections. */
  Advanced(
    label = "Advanced",
    tagline = null,
  ),
}
