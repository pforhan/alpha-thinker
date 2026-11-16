package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext

/**
 * App-private managed storage for generated export files. Implementations must
 * keep files under a platform-appropriate private directory and treat paths/refs
 * returned from [pathFor] as canonical internal [alphainterplanetary.thinker.model.ExportArtifact.storageRef] values.
 */
interface ExportFileStore {
  /** Canonical managed path/ref for an export artifact file. */
  fun pathFor(artifactId: String, extension: String): String

  /** Writes [content] to [path], creating parent directories as needed. */
  suspend fun writeText(path: String, content: String): Result<Unit>

  /** Reads file content, or null if missing/unreadable. */
  suspend fun readText(path: String): String?

  /** Deletes the file at [path]. Returns true if removed or already absent. */
  suspend fun delete(path: String): Boolean
}

expect fun provideExportFileStore(platformContext: PlatformContext): ExportFileStore
