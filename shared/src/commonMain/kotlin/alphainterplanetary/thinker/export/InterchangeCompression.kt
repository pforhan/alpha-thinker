package alphainterplanetary.thinker.export

/** Compresses [bytes] as gzip for portable `.thinker` interchange files. */
expect fun gzipCompress(bytes: ByteArray): ByteArray

/** Decompresses gzip-compressed [bytes] from a portable `.thinker` interchange file. */
expect fun gzipDecompress(bytes: ByteArray): ByteArray
