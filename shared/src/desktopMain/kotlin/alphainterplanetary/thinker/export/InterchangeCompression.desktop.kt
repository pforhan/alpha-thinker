package alphainterplanetary.thinker.export

import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

actual fun gzipCompress(bytes: ByteArray): ByteArray {
  val out = Buffer()
  GzipSink(out).buffer().use { it.write(bytes) }
  return out.readByteArray()
}

actual fun gzipDecompress(bytes: ByteArray): ByteArray =
  GzipSource(Buffer().write(bytes)).buffer().use { it.readByteArray() }
