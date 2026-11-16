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
  override suspend fun save(filename: String, mimeType: String, content: String): String? =
    if (download(filename, mimeType, content)) filename else null

  override suspend fun share(filename: String, mimeType: String, content: String): Boolean =
    download(filename, mimeType, content)

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
}
