package alphainterplanetary.thinker.engine

/**
 * Where an engine appears in the picker. Tiers are a presentation grouping
 * (ENGINE-CONFIG-PLAN.md stage 3): `Offline` and `Free` are always visible,
 * `Local` becomes real once downloaded models land (MODEL-CATALOG-PLAN.md
 * stage 3), and every user-configured connection lives under `Advanced` so the
 * ever-growing list of endpoints never crowds the common choice.
 */
enum class EngineTier {
  /** The built-in question library, always available. */
  Offline,

  /** The free Alpha Interplanetary backend (a disabled placeholder for now). */
  Free,

  /** Downloaded in-process models. */
  Local,

  /** Configured Remote connections and the system on-device runtime. */
  Advanced,
}
