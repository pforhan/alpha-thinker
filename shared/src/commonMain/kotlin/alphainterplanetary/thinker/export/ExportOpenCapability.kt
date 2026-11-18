package alphainterplanetary.thinker.export

/** Describes whether and how an export can be opened in a viewer/handler. */
sealed interface ExportOpenCapability {
  /** No viewer/handler is available for this export. */
  data object Unavailable : ExportOpenCapability

  /**
   * Exactly one handler is available. [appLabel] is the handler/application name
   * when known (e.g. on Android), or null when only a generic default is known.
   */
  data class Single(val appLabel: String? = null) : ExportOpenCapability

  /** Multiple handlers are available; opening should prompt the user to choose. */
  data class Multiple(val count: Int) : ExportOpenCapability

  val isAvailable: Boolean get() = this !is Unavailable
}
