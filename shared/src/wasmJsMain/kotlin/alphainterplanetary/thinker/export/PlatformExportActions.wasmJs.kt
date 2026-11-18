package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  WasmPlatformExportActions()

@OptIn(ExperimentalWasmJsInterop::class)
private class WasmPlatformExportActions : PlatformExportActions {
  // Native file sharing is available only when the Web Share API can share a
  // File with the given type in this browser/context. Probed with a tiny sample.
  override val supportsShare: Boolean = canShareFiles(sampleName, "text/plain", "x")
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
    content: String,
  ): String? = if (download(filename, mimeType, content)) filename else null

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean {
    // Prefer native Web Share (with files) where supported; fall back to download.
    if (canShareFiles(filename, mimeType, content) &&
      shareFile(filename.toJsString(), mimeType.toJsString(), content.toJsString())
    ) {
      return true
    }
    return download(filename, mimeType, content)
  }

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean {
    // Try opening in a new tab/viewer; fall back to download so the user still
    // gets the file with the correct name and MIME type.
    if (supportsOpen &&
      openInNewTab(filename.toJsString(), mimeType.toJsString(), content.toJsString())
    ) {
      return true
    }
    return download(filename, mimeType, content)
  }

  private fun download(filename: String, mimeType: String, content: String): Boolean =
    runCatching {
      val parts = JsArray<JsAny?>()
      parts[0] = content.toJsString()
      val blob = Blob(parts, BlobPropertyBag(type = mimeType))
      val url = URL.createObjectURL(blob)
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

  private fun canShareFiles(filename: String, mimeType: String, content: String): Boolean =
    runCatching {
      canShareFileJs(filename.toJsString(), mimeType.toJsString(), content.toJsString())
    }.getOrDefault(false)

  private companion object {
    private const val sampleName = "export.txt"
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun hasWindowOpen(): Boolean = js(
  "typeof window !== 'undefined' && typeof window.open === 'function'"
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun canShareFileJs(filename: JsString, mimeType: JsString, content: JsString): Boolean = js(
  """(function() {
    try {
      if (typeof navigator === 'undefined' || typeof navigator.share !== 'function') return false;
      var file = new File([content], filename, { type: mimeType });
      var data = { files: [file] };
      if (typeof navigator.canShare === 'function') return !!navigator.canShare(data);
      // share() exists but canShare() may be absent; assume files may be shareable.
      return true;
    } catch (e) { return false; }
  })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun shareFile(filename: JsString, mimeType: JsString, content: JsString): Boolean = js(
  """(function() {
    try {
      var file = new File([content], filename, { type: mimeType });
      var data = { files: [file], title: filename };
      if (typeof navigator.canShare === 'function' && !navigator.canShare(data)) return false;
      navigator.share(data).catch(function() {});
      return true;
    } catch (e) { return false; }
  })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun openInNewTab(filename: JsString, mimeType: JsString, content: JsString): Boolean = js(
  """(function() {
    try {
      var blob = new Blob([content], { type: mimeType });
      var url = URL.createObjectURL(blob);
      var w = window.open(url, '_blank');
      if (!w) { URL.revokeObjectURL(url); return false; }
      setTimeout(function() { try { URL.revokeObjectURL(url); } catch (e) {} }, 60000);
      return true;
    } catch (e) { return false; }
  })()"""
)
