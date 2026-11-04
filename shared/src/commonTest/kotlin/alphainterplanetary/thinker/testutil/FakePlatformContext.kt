package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.di.DeviceInfo
import alphainterplanetary.thinker.di.PlatformContext

/**
 * A [PlatformContext] over an in-memory file set, for tests that need to say
 * "this model file exists" without touching the host filesystem.
 *
 * [directory] is the model directory the host would report; null models a
 * platform with no local-model storage (the default in [PlatformContext]).
 * [evalDir] and [textFiles] do the same for evaluation rows.
 */
class FakePlatformContext(
  private val directory: String? = "/models",
  private val evalDir: String? = "/evals",
  val device: DeviceInfo = DeviceInfo(platform = "test", os = "TestOS", device = "Fake"),
) : PlatformContext {
  /** Paths the fake host considers present. */
  val files: MutableSet<String> = mutableSetOf()

  /** Text files by absolute path. */
  val textFiles: MutableMap<String, String> = mutableMapOf()

  override fun databaseFile(name: String): String = "/db/$name"

  override fun modelDirectory(): String? = directory

  override fun fileExists(path: String): Boolean = path in files

  override fun deleteFile(path: String): Boolean {
    files.remove(path)
    textFiles.remove(path)
    return path !in files
  }

  override fun evalDirectory(): String? = evalDir

  override fun readTextFile(path: String): String? = textFiles[path]

  override fun writeTextFile(path: String, text: String): Boolean {
    textFiles[path] = text
    return true
  }

  override fun listFiles(directory: String): List<String> = textFiles.keys
    .filter { it.startsWith("$directory/") && !it.removePrefix("$directory/").contains('/') }
    .map { it.substringAfterLast('/') }

  override fun deviceInfo(): DeviceInfo = device
}
