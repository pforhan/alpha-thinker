package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.export.ExportFileStore

/** In-memory [ExportFileStore] for tests. */
class FakeExportFileStore : ExportFileStore {
  val files: MutableMap<String, String> = mutableMapOf()

  override fun pathFor(artifactId: String, extension: String): String =
    "mem://exports/$artifactId.$extension"

  override suspend fun writeText(path: String, content: String): Result<Unit> = runCatching {
    files[path] = content
  }

  override suspend fun readText(path: String): String? = files[path]

  override suspend fun delete(path: String): Boolean {
    files.remove(path)
    return true
  }
}
