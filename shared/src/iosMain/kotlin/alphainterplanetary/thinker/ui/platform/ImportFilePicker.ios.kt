package alphainterplanetary.thinker.ui.platform

import alphainterplanetary.thinker.export.topViewController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberInterchangeFilePicker(
  onPicked: (List<PickedImportFile>) -> Unit,
  onMessage: (String) -> Unit,
): (() -> Unit)? {
  val scope = rememberCoroutineScope()
  return remember(onPicked, onMessage) {
    {
      scope.launch {
        val files = presentInterchangeImportPicker()
        if (files.isEmpty()) return@launch
        val picked = withContext(Dispatchers.Default) {
          files.mapNotNull { path -> readPickedImportFile(path) }
        }
        if (picked.isEmpty()) {
          onMessage("Unable to read selected file(s)")
        } else {
          if (picked.size != files.size) {
            onMessage("Some selected files could not be read")
          }
          onPicked(picked)
        }
      }
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun readPickedImportFile(path: String): PickedImportFile? = runCatching {
  val data = NSData.dataWithContentsOfFile(path) ?: return null
  val length = data.length.toInt()
  val bytes = ByteArray(length)
  if (length > 0) {
    bytes.usePinned { pinned ->
      memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
  }
  val name = path.substringAfterLast('/')
  PickedImportFile(name = name, bytes = bytes)
}.getOrNull()

private suspend fun presentInterchangeImportPicker(): List<String> =
  withContext(Dispatchers.Main) {
    val presenter = topViewController() ?: return@withContext emptyList()
    suspendCancellableCoroutine { continuation ->
      val delegate = ImportPickerDelegate { result ->
        if (continuation.isActive) continuation.resume(result)
      }
      runCatching {
        ImportPickerRetainer.retain(delegate)
        // Use broad document types so .thinker (custom/gzip/octet-stream) files are selectable.
        val picker = UIDocumentPickerViewController(
          documentTypes = listOf("public.data", "public.json", "public.text"),
          inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        picker.allowsMultipleSelection = true
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
      }.onFailure {
        if (continuation.isActive) continuation.resume(emptyList())
      }
    }
  }

private object ImportPickerRetainer {
  private val retained = mutableListOf<Any>()

  fun retain(delegate: Any) {
    retained.add(delegate)
    if (retained.size > 10) {
      retained.subList(0, retained.size - 10).clear()
    }
  }
}

private class ImportPickerDelegate(
  private val onResult: (List<String>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
  override fun documentPicker(
    controller: UIDocumentPickerViewController,
    didPickDocumentsAtURLs: List<*>,
  ) {
    val paths = didPickDocumentsAtURLs.mapNotNull { item ->
      val url = item as? NSURL ?: return@mapNotNull null
      url.path ?: url.absoluteString
    }
    onResult(paths)
  }

  override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
    onResult(emptyList())
  }
}

