package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext

/**
 * App-private managed storage for generated export files.
 *
 * The canonical persisted value is a stable, storage-relative `ref` (returned by
 * [filenameFor]) so exports remain valid if the platform's base directory changes.
 * [resolve] maps a ref to a platform-specific absolute path/location for file IO
 * and platform actions.
 *
 * Byte methods are the primitive operations so binary exports (e.g. compressed
 * portable interchange) are stored correctly; text methods are UTF-8 conveniences.
 */
interface ExportFileStore {
  /** Canonical stored filename/ref for an export artifact file (persisted as storageRef). */
  fun filenameFor(artifactId: String, extension: String): String

  /** Resolves a stored [ref] to an absolute path/location. */
  fun resolve(ref: String): String

  /** Writes raw [bytes] for [ref], creating parent directories as needed. */
  suspend fun writeBytes(ref: String, bytes: ByteArray): Result<Unit>

  /** Reads raw bytes for [ref], or null if missing/unreadable. */
  suspend fun readBytes(ref: String): ByteArray?

  /** Writes UTF-8 [content] for [ref], creating parent directories as needed. */
  suspend fun writeText(ref: String, content: String): Result<Unit> =
    writeBytes(ref, content.encodeToByteArray())

  /** Reads UTF-8 text content for [ref], or null if missing/unreadable. */
  suspend fun readText(ref: String): String? =
    readBytes(ref)?.decodeToString()

  /** Deletes the file for [ref]. Returns true if removed or already absent. */
  suspend fun delete(ref: String): Boolean
}

expect fun provideExportFileStore(platformContext: PlatformContext): ExportFileStore
