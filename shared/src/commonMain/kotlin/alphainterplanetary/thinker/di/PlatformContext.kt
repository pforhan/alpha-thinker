package alphainterplanetary.thinker.di

/**
 * The host seam the shared database builder needs: where a database file lives.
 * Every other part of persistence — the entities, DAOs, and builder wiring — is
 * common code, so this interface asks each target exactly one question rather
 * than handing the whole platform object around.
 *
 * It also answers the storage questions a local model needs — where model files
 * live and whether one is already there. The defaults describe a platform with
 * no local storage, which is exactly what web and iOS are until their runtimes
 * land; Android and desktop override them.
 */
interface PlatformContext {
  /** Where the database file called [name] lives: an absolute path, or on web a bare file name. */
  fun databaseFile(name: String): String

  /** Where downloaded model files live, or null when the platform cannot store them. */
  fun modelDirectory(): String? = null

  /** Whether a model file exists at [path]. */
  fun fileExists(path: String): Boolean = false

  /** Deletes the file at [path]; returns whether it no longer exists. */
  fun deleteFile(path: String): Boolean = false
}
