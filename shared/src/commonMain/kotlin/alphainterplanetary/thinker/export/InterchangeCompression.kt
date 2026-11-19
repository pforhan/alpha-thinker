package alphainterplanetary.thinker.export

import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

/** Compresses [bytes] as gzip for portable `.thinker` interchange files. */
fun gzipCompress(bytes: ByteArray): ByteArray {
  val out = Buffer()
  GzipSink(out).buffer().use { it.write(bytes) }
  return out.readByteArray()
}

/** Decompresses gzip-compressed [bytes] from a portable `.thinker` interchange file. */
fun gzipDecompress(bytes: ByteArray): ByteArray =
  GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray() }
