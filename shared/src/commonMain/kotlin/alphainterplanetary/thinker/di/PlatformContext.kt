package alphainterplanetary.thinker.di

/**
 * The host seam the shared database builder needs: where a database file lives.
 * Every other part of persistence — the entities, DAOs, and builder wiring — is
 * common code, so this interface asks each target exactly one question rather
 * than handing the whole platform object around.
 *
 * It also answers the storage questions a local model and the evaluation harness
 * need — where model files live, whether one is already there, and where a
 * measured row is written — plus a description of the device for grouping rows.
 * The defaults describe a platform with no local storage, which is exactly what
 * web and iOS are until their runtimes land; Android and desktop override them.
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

  /**
   * Where evaluation rows live, or null when the platform cannot store them.
   * The harness writes one JSON file per `(model, run)` here so a sweep that is
   * killed mid-flight leaves a resumable row rather than only a memory of one.
   */
  fun evalDirectory(): String? = null

  /** The text of the file at [path], or null when it cannot be read. */
  fun readTextFile(path: String): String? = null

  /**
   * Replaces the file at [path] with [text]. Returns false when the platform
   * cannot write; callers treat a failed write as a lost measurement, not as a
   * reason to stop the sweep.
   */
  fun writeTextFile(path: String, text: String): Boolean = false

  /** The bare names of the files directly under [directory]. */
  fun listFiles(directory: String): List<String> = emptyList()

  /** The device this build is running on, for grouping eval rows. */
  fun deviceInfo(): DeviceInfo = DeviceInfo(platform = "unknown")
}
