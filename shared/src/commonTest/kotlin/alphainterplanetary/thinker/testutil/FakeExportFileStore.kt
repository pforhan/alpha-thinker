package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.export.ExportFileStore

/** In-memory [ExportFileStore] for tests. */
class FakeExportFileStore : ExportFileStore {
  val files: MutableMap<String, String> = mutableMapOf()

  override fun filenameFor(artifactId: String, extension: String): String =
    "$artifactId.$extension"

  override fun resolve(ref: String): String =
    if (ref.startsWith("mem://exports/")) ref else "mem://exports/$ref"

  override suspend fun writeText(ref: String, content: String): Result<Unit> = runCatching {
    files[resolve(ref)] = content
  }

  override suspend fun readText(ref: String): String? = files[resolve(ref)]

  override suspend fun delete(ref: String): Boolean {
    files.remove(resolve(ref))
    return true
  }
}
