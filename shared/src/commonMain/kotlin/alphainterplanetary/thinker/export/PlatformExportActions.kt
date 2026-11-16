package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext

/**
 * Platform-specific save/share/open operations for generated exports.
 * Implementations should degrade gracefully when a capability is unavailable.
 */
interface PlatformExportActions {
  /**
   * Saves [content] as [filename] ([mimeType]) to a user-chosen or platform
   * default location. Returns a storage reference/path/URI on success, or null
   * if the user cancelled or saving is unavailable.
   */
  suspend fun save(filename: String, mimeType: String, content: String): String?

  /**
   * Shares or opens [content] using the platform's share/open mechanism.
   * Returns true if the action was initiated successfully.
   */
  suspend fun share(filename: String, mimeType: String, content: String): Boolean
}

expect fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions
