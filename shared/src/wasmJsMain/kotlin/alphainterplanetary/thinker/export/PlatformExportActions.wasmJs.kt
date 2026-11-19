package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  WasmPlatformExportActions()

@OptIn(ExperimentalWasmJsInterop::class)
private class WasmPlatformExportActions : PlatformExportActions {
  // Native file sharing is available only when the Web Share API can share a
  // File with the given type in this browser/context. Probed with a tiny sample.
  override val supportsShare: Boolean = canShareFilesBytes(
    sampleName.toJsString(),
    "text/plain".toJsString(),
    byteArrayOf('x'.code.toByte()).toJsNumberArray(),
  )
  override val supportsOpen: Boolean = hasWindowOpen()
  override val saveActionLabel: String = "Download"

  override suspend fun openCapability(
    filename: String,
    mimeType: String,
    internalPath: String,
  ): ExportOpenCapability = when {
    supportsOpen -> ExportOpenCapability.Single(if (mimeType == "text/html") "Browser" else "New tab")
    else -> ExportOpenCapability.Unavailable
  }

  override suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): String? = if (download(filename, mimeType, content)) filename else null

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean {
    // Prefer native Web Share (with files) where supported; fall back to download.
    val bytes = content.toJsNumberArray()
    if (canShareFilesBytes(filename.toJsString(), mimeType.toJsString(), bytes) &&
      shareFileBytes(filename.toJsString(), mimeType.toJsString(), bytes)
    ) {
      return true
    }
    return download(filename, mimeType, content)
  }

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean {
    // Try opening in a new tab/viewer; fall back to download so the user still
    // gets the file with the correct name and MIME type.
    val bytes = content.toJsNumberArray()
    if (supportsOpen &&
      openInNewTabBytes(filename.toJsString(), mimeType.toJsString(), bytes)
    ) {
      return true
    }
    return download(filename, mimeType, content)
  }

  private fun download(filename: String, mimeType: String, content: ByteArray): Boolean =
    runCatching {
      val url = createObjectUrl(content.toJsNumberArray(), mimeType.toJsString())
      val anchor = (document.createElement("a") as HTMLAnchorElement).apply {
        href = url
        this.download = filename
        style.display = "none"
      }
      document.body?.appendChild(anchor)
      anchor.click()
      document.body?.removeChild(anchor)
      URL.revokeObjectURL(url)
      true
    }.getOrDefault(false)

  private companion object {
    private const val sampleName = "export.txt"
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun ByteArray.toJsNumberArray(): JsArray<JsNumber> {
  val arr = JsArray<JsNumber>()
  forEachIndexed { i, b -> arr[i] = (b.toInt() and 0xFF).toJsNumber() }
  return arr
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun createObjectUrl(bytes: JsArray<JsNumber>, mimeType: JsString): String = js(
  """(function() {
    var u8 = new Uint8Array(bytes);
    var blob = new Blob([u8], { type: mimeType });
    return URL.createObjectURL(blob);
  })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun hasWindowOpen(): Boolean = js(
  "typeof window !== 'undefined' && typeof window.open === 'function'"
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun canShareFilesBytes(filename: JsString, mimeType: JsString, bytes: JsArray<JsNumber>): Boolean = js(
  """(function() {
    try {
      if (typeof navigator === 'undefined' || typeof navigator.share !== 'function') return false;
      var file = new File([new Uint8Array(bytes)], filename, { type: mimeType });
      var data = { files: [file] };
      if (typeof navigator.canShare === 'function') return !!navigator.canShare(data);
      // share() exists but canShare() may be absent; assume files may be shareable.
      return true;
    } catch (e) { return false; }
  })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun shareFileBytes(filename: JsString, mimeType: JsString, bytes: JsArray<JsNumber>): Boolean = js(
  """(function() {
    try {
      var file = new File([new Uint8Array(bytes)], filename, { type: mimeType });
      var data = { files: [file], title: filename };
      if (typeof navigator.canShare === 'function' && !navigator.canShare(data)) return false;
      navigator.share(data).catch(function() {});
      return true;
    } catch (e) { return false; }
  })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun openInNewTabBytes(filename: JsString, mimeType: JsString, bytes: JsArray<JsNumber>): Boolean = js(
  """(function() {
    try {
      var blob = new Blob([new Uint8Array(bytes)], { type: mimeType });
      var url = URL.createObjectURL(blob);
      var w = window.open(url, '_blank');
      if (!w) { URL.revokeObjectURL(url); return false; }
      setTimeout(function() { try { URL.revokeObjectURL(url); } catch (e) {} }, 60000);
      return true;
    } catch (e) { return false; }
  })()"""
)
