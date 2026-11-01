package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.di.PlatformContext

/**
 * A [PlatformContext] over an in-memory file set, for tests that need to say
 * "this model file exists" without touching the host filesystem.
 *
 * [directory] is the model directory the host would report; null models a
 * platform with no local-model storage (the default in [PlatformContext]).
 */
class FakePlatformContext(
  private val directory: String? = "/models",
) : PlatformContext {
  /** Paths the fake host considers present. */
  val files: MutableSet<String> = mutableSetOf()

  override fun databaseFile(name: String): String = "/db/$name"

  override fun modelDirectory(): String? = directory

  override fun fileExists(path: String): Boolean = path in files

  override fun deleteFile(path: String): Boolean {
    files.remove(path)
    return path !in files
  }
}
