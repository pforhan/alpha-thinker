package alphainterplanetary.thinker.downloads

import alphainterplanetary.thinker.di.PlatformContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Direct HTTP(S) download into app storage.
 *
 * A transfer is one resumable `.part` file: a rerun sends `Range` from its
 * current length, a server that ignores the range (HTTP 200) restarts it, and
 * only the verified finished file is renamed onto the destination. Failures
 * leave the `.part` in place so the next attempt resumes instead of starting
 * over. Every phase — transfer, SHA-256 verification, the final rename — is
 * reported on the same event stream, because the caller treats them as one
 * `ModelFileDownload` task.
 *
 * Hugging Face answers a `resolve/main` URL with a redirect to a CDN, so
 * redirects are followed by hand rather than by [HttpURLConnection]'s automatic
 * mode: the `Range` and identity-encoding headers have to travel to the final
 * host to keep a resumed transfer from quietly restarting.
 */
class AndroidModelFileTransferClient(
  private val platformContext: PlatformContext,
) : ModelFileTransferClient {

  override fun download(spec: ModelDownloadSpec): Flow<LocalDownloadEvent> = flow {
    var connection: HttpURLConnection? = null
    try {
      if (spec.extract) {
        throw IOException("Archive extraction is not supported for local models")
      }
      val destination = destinationFile(spec)
      val part = File("${destination.path}.part")
      destination.parentFile?.mkdirs()

      val resumeFrom = part.length().coerceAtLeast(0L)
      checkDiskSpace(spec.expectedBytes, resumeFrom, destination)

      val active = open(spec.url, resumeFrom)
      connection = active
      val responseCode = active.responseCode
      val append = responseCode == HttpURLConnection.HTTP_PARTIAL
      val rangeSatisfied = responseCode == HTTP_RANGE_NOT_SATISFIABLE
      if (responseCode != HttpURLConnection.HTTP_OK && !append && !rangeSatisfied) {
        throw IOException("Download failed with HTTP $responseCode")
      }

      var done = if (append) resumeFrom else 0L
      if (!append && !rangeSatisfied && part.exists()) part.delete()
      val contentLength = active.contentLengthLong
      val total = spec.expectedBytes
        ?: when {
          rangeSatisfied -> resumeFrom
          contentLength > 0L -> done + contentLength
          else -> null
        }

      var throughput: Long? = null
      var windowStartBytes = done
      var windowStartAt = System.currentTimeMillis()
      var lastReportAt = 0L

      suspend fun report(bytesDone: Long, bps: Long?) {
        emit(
          LocalDownloadEvent(
            phase = LocalDownloadPhase.Downloading,
            bytesDone = bytesDone,
            bytesTotal = total,
            throughputBps = bps,
          ),
        )
      }

      report(done, null)

      if (!rangeSatisfied) {
        active.inputStream.use { input ->
          FileOutputStream(part, append).use { output ->
            val buffer = ByteArray(BufferSize)
            while (true) {
              currentCoroutineContext().ensureActive()
              val read = input.read(buffer)
              if (read < 0) break
              output.write(buffer, 0, read)
              done += read
              val now = System.currentTimeMillis()
              if (now - lastReportAt >= ReportIntervalMillis) {
                val elapsed = (now - windowStartAt).coerceAtLeast(1L)
                throughput = ((done - windowStartBytes) * 1_000L) / elapsed
                report(done, throughput)
                windowStartBytes = done
                windowStartAt = now
                lastReportAt = now
              }
            }
          }
        }
      }
      active.disconnect()
      connection = null

      report(done, throughput)

      val expected = spec.expectedSha256
      if (!expected.isNullOrBlank()) {
        emit(
          LocalDownloadEvent(
            phase = LocalDownloadPhase.Verifying,
            bytesDone = done,
            bytesTotal = total,
          ),
        )
        val actual = sha256(part)
        if (!actual.equals(expected, ignoreCase = true)) {
          part.delete()
          throw IOException("Checksum mismatch: expected $expected, got $actual")
        }
      }

      if (destination.exists()) destination.delete()
      if (!part.renameTo(destination)) {
        part.copyTo(destination, overwrite = true)
        part.delete()
      }
      emit(
        LocalDownloadEvent(
          phase = LocalDownloadPhase.Succeeded,
          bytesDone = done,
          bytesTotal = total,
        ),
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      emit(
        LocalDownloadEvent(
          phase = LocalDownloadPhase.Failed,
          error = e.message ?: e.toString(),
        ),
      )
    } finally {
      // Covers the failure paths that never reached the happy-path disconnect.
      connection?.disconnect()
    }
  }.flowOn(Dispatchers.IO)

  private fun destinationFile(spec: ModelDownloadSpec): File {
    val explicit = spec.destination
    if (!explicit.isNullOrBlank()) return File(explicit)
    val directory = platformContext.modelDirectory()
      ?: throw IOException("This platform has no model storage")
    val name = spec.modelId.substringAfterLast('/').ifBlank { "model.litertlm" }
    return File(directory, name)
  }

  /**
   * Refuses a transfer that cannot fit before spending bandwidth on it. Only the
   * bytes still to fetch count: the `.part` is already on disk and its space is
   * no longer reported as usable.
   */
  private fun checkDiskSpace(expectedBytes: Long?, resumeFrom: Long, destination: File) {
    val required = expectedBytes ?: return
    val remaining = (required - resumeFrom).coerceAtLeast(0L)
    val usable = destination.parentFile?.usableSpace ?: return
    if (usable < remaining) {
      throw IOException(
        "Not enough free space: need $remaining bytes, have $usable",
      )
    }
  }

  /** Opens [url], following redirects while preserving the resume headers. */
  private fun open(url: String, resumeFrom: Long): HttpURLConnection {
    var current = url
    repeat(MaxRedirects) {
      val connection = (URL(current).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = ConnectTimeoutMillis
        readTimeout = ReadTimeoutMillis
        setRequestProperty("User-Agent", UserAgent)
        setRequestProperty("Accept-Encoding", "identity")
        if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
      }
      connection.connect()
      val code = connection.responseCode
      if (code in RedirectCodes) {
        val location = connection.getHeaderField("Location")
        connection.disconnect()
        if (location.isNullOrBlank()) throw IOException("Redirect without a Location header")
        current = URL(URL(current), location).toString()
      } else {
        return connection
      }
    }
    throw IOException("Too many redirects")
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(BufferSize)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private companion object {
    const val BufferSize = 64 * 1024
    const val ReportIntervalMillis = 200L
    const val ConnectTimeoutMillis = 30_000
    const val ReadTimeoutMillis = 60_000
    const val MaxRedirects = 6
    const val UserAgent = "AlphaThinker/1.0 (Android)"
    const val HTTP_RANGE_NOT_SATISFIABLE = 416
    val RedirectCodes = setOf(301, 302, 303, 307, 308)
  }
}
