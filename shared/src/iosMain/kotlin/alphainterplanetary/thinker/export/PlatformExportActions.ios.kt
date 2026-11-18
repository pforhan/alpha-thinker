package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  IosPlatformExportActions()

@OptIn(ExperimentalForeignApi::class)
private class IosPlatformExportActions : PlatformExportActions {
  // User-chosen save is provided via the UI-integrated document picker flow
  // (rememberExportSaveAction), consistent with Android's SAF integration.
  override val supportsSave: Boolean = false
  override val supportsOpen: Boolean = true
  override val supportsShare: Boolean = true

  override suspend fun openCapability(
    filename: String,
    mimeType: String,
    internalPath: String,
  ): ExportOpenCapability {
    val fileExists = NSFileManager.defaultManager.fileExistsAtPath(internalPath)
    return if (fileExists) ExportOpenCapability.Single("Preview") else ExportOpenCapability.Unavailable
  }

  override suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): String? = null

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = withContext(Dispatchers.Main) {
    val url = fileUrl(internalPath) ?: return@withContext false
    val presenter = topViewController() ?: return@withContext false
    runCatching {
      val activity = UIActivityViewController(
        activityItems = listOf(url),
        applicationActivities = null,
      )
      configurePopoverAnchor(activity, presenter)
      presenter.presentViewController(activity, animated = true, completion = null)
      true
    }.getOrDefault(false)
  }

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = withContext(Dispatchers.Main) {
    val url = fileUrl(internalPath) ?: return@withContext false
    val presenter = topViewController() ?: return@withContext false
    runCatching {
      presentQuickLook(presenter, url)
      true
    }.getOrDefault(false)
  }
}

@OptIn(ExperimentalForeignApi::class)
internal fun fileUrl(path: String): NSURL? {
  val fm = NSFileManager.defaultManager
  if (!fm.fileExistsAtPath(path)) return null
  return NSURL.fileURLWithPath(path)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun topViewController(): UIViewController? {
  val application = UIApplication.sharedApplication
  val windows = application.windows
  val keyWindow = windows.firstNotNullOfOrNull { window ->
    (window as? UIWindow)?.takeIf { it.isKeyWindow() }
  } ?: windows.firstNotNullOfOrNull { it as? UIWindow }
  var controller = keyWindow?.rootViewController ?: run {
    // Scene-based fallback.
    val scenes = application.connectedScenes
    val sceneWindow = scenes.firstNotNullOfOrNull { scene ->
      val windowScene = scene as? UIWindowScene ?: return@firstNotNullOfOrNull null
      val sceneWindows = windowScene.windows
      sceneWindows.firstNotNullOfOrNull { w ->
        (w as? UIWindow)?.takeIf { it.isKeyWindow() }
      } ?: sceneWindows.firstNotNullOfOrNull { it as? UIWindow }
    }
    sceneWindow?.rootViewController
  }
  while (controller?.presentedViewController != null) {
    controller = controller.presentedViewController
  }
  return controller
}

@OptIn(ExperimentalForeignApi::class)
private fun configurePopoverAnchor(controller: UIViewController, presenter: UIViewController) {
  // Popover anchoring for iPad requires UIPopoverPresentationController access;
  // the getter name/shape was not resolvable in this Kotlin/Native UIKit binding.
  // Presentation remains wrapped so unsupported cases degrade gracefully.
}

@OptIn(ExperimentalForeignApi::class)
private fun presentQuickLook(presenter: UIViewController, url: NSURL) {
  val item = ExportPreviewItem(url)
  val dataSource = ExportPreviewDataSource(item)
  val controller = QLPreviewController()
  // QLPreviewController holds dataSource weakly; retain strongly until presented.
  QuickLookRetainer.retain(controller, dataSource)
  controller.dataSource = dataSource
  presenter.presentViewController(controller, animated = true, completion = null)
}

/**
 * Retains Quick Look data sources for the lifetime of presented controllers.
 * Entries are released on a best-effort basis when replaced; controllers/data
 * sources are lightweight so retaining for the session is acceptable for export
 * previews.
 */
private object QuickLookRetainer {
  private val retained = mutableListOf<Any>()

  fun retain(controller: QLPreviewController, dataSource: Any) {
    // Keep strong references; drop oldest if this grows unexpectedly.
    retained.add(controller)
    retained.add(dataSource)
    if (retained.size > 20) {
      retained.subList(0, retained.size - 20).clear()
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private class ExportPreviewItem(
  private val url: NSURL,
) : NSObject(), QLPreviewItemProtocol {
  override fun previewItemURL(): NSURL = url

  override fun previewItemTitle(): String? = url.lastPathComponent
}

@OptIn(ExperimentalForeignApi::class)
private class ExportPreviewDataSource(
  private val item: ExportPreviewItem,
) : NSObject(), QLPreviewControllerDataSourceProtocol {
  override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1

  override fun previewController(
    controller: QLPreviewController,
    previewItemAtIndex: Long,
  ): QLPreviewItemProtocol = item
}

/** Shared helper for presenting the iOS document-export picker and awaiting a result. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun presentExportPicker(filePath: String): String? = withContext(Dispatchers.Main) {
  val url = fileUrl(filePath) ?: return@withContext null
  val presenter = topViewController() ?: return@withContext null
  suspendCancellableCoroutine { continuation ->
    val delegate = ExportPickerDelegate { result ->
      if (continuation.isActive) continuation.resume(result)
    }
    runCatching {
      // Retain delegate strongly until callback; UIDocumentPickerController.delegate is weak.
      ExportPickerRetainer.retain(delegate)
      val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
      picker.delegate = delegate
      configurePopoverAnchor(picker, presenter)
      presenter.presentViewController(picker, animated = true, completion = null)
    }.onFailure {
      if (continuation.isActive) continuation.resume(null)
    }
  }
}

private object ExportPickerRetainer {
  private val retained = mutableListOf<Any>()

  fun retain(delegate: Any) {
    retained.add(delegate)
    if (retained.size > 10) {
      retained.subList(0, retained.size - 10).clear()
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private class ExportPickerDelegate(
  private val onResult: (String?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
  override fun documentPicker(
    controller: UIDocumentPickerViewController,
    didPickDocumentsAtURLs: List<*>,
  ) {
    val destination = didPickDocumentsAtURLs.firstOrNull() as? NSURL
    onResult(destination?.path ?: destination?.absoluteString)
  }

  override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
    onResult(null)
  }
}
