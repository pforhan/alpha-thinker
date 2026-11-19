package alphainterplanetary.thinker.testutil

import alphainterplanetary.thinker.export.ExportFileStore

/** In-memory [ExportFileStore] for tests. */
class FakeExportFileStore : ExportFileStore {
  val files: MutableMap<String, ByteArray> = mutableMapOf()

  override fun filenameFor(artifactId: String, extension: String): String =
    "$artifactId.$extension"

  override fun resolve(ref: String): String =
    if (ref.startsWith("mem://exports/")) ref else "mem://exports/$ref"

  override suspend fun writeBytes(ref: String, bytes: ByteArray): Result<Unit> = runCatching {
    files[resolve(ref)] = bytes
  }

  override suspend fun readBytes(ref: String): ByteArray? = files[resolve(ref)]

  override suspend fun delete(ref: String): Boolean {
    files.remove(resolve(ref))
    return true
  }
}
