package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext

/**
 * Platform-specific save/share/open operations for generated exports backed by a
 * canonical internal managed file. [internalPath] is the managed export file ref;
 * [content] is its rendered text (loaded by the caller from the canonical file).
 *
 * Implementations must not treat user-destination results as canonical storage
 * refs. Capabilities may be unavailable on some platforms and should degrade
 * gracefully (return null/false).
 */
interface PlatformExportActions {
  /**
   * Saves/exports a copy to a user-chosen or platform destination. Returns the
   * destination ref/path/URI on success, or null if cancelled/unavailable.
   */
  suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): String?

  /** Shares or opens the export via a platform-native mechanism. */
  suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean

  /** Opens the export in a viewer/default handler when supported. */
  suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean
}

expect fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions
