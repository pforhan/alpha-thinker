package alphainterplanetary.thinker.di

/**
 * The host seam the shared database builder needs: where a database file lives.
 * Every other part of persistence — the entities, DAOs, and builder wiring — is
 * common code, so this interface asks each target exactly one question rather
 * than handing the whole platform object around.
 */
interface PlatformContext {
  /** Where the database file called [name] lives: an absolute path, or on web a bare file name. */
  fun databaseFile(name: String): String

  /** App-private directory for managed export files (absolute path or platform-managed location key). */
  fun exportsDirectoryPath(): String
}
