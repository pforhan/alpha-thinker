package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext

/**
 * Platform-specific save/share/open operations for generated exports backed by a
 * canonical internal managed file. [internalPath] is the managed export file ref;
 * [content] contains the rendered bytes (loaded by the caller from the canonical
 * file) when in-memory data is needed (e.g. web download/share).
 *
 * Implementations must not treat user-destination results as canonical storage
 * refs. Capabilities may be unavailable on some platforms and should degrade
 * gracefully (return null/false).
 */
interface PlatformExportActions {
  /** Whether saving/exporting a copy to a user destination is a distinct action. */
  val supportsSave: Boolean get() = true

  /** Whether opening in a viewer/default handler is a distinct action. */
  val supportsOpen: Boolean get() = true

  /** Whether sharing via a platform-native mechanism is a distinct action. */
  val supportsShare: Boolean get() = true

  /** UI label for the save action (e.g. "Save" or "Download"). */
  val saveActionLabel: String get() = "Save"

  /**
   * Returns how [open] can be handled for this export (viewer availability and,
   * when known, the handler label/count). Defaults to a generic capability derived
   * from [supportsOpen]; platforms may override for per-format/per-file detection.
   */
  suspend fun openCapability(
    filename: String,
    mimeType: String,
    internalPath: String,
  ): ExportOpenCapability =
    if (supportsOpen) ExportOpenCapability.Single() else ExportOpenCapability.Unavailable

  /**
   * Saves/exports a copy to a user-chosen or platform destination. Returns the
   * destination ref/path/URI on success, or null if cancelled/unavailable.
   */
  suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): String?

  /** Shares the export via a platform-native mechanism. */
  suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean

  /** Opens the export in a viewer/default handler when supported. */
  suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean
}

expect fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions
