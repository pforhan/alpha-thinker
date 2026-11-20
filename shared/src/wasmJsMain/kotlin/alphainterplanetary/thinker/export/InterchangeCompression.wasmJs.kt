package alphainterplanetary.thinker.export

actual fun gzipCompress(bytes: ByteArray): ByteArray =
  throw UnsupportedOperationException("Gzip compression is not supported on this platform")

actual fun gzipDecompress(bytes: ByteArray): ByteArray =
  throw UnsupportedOperationException("Gzip decompression is not supported on this platform")
