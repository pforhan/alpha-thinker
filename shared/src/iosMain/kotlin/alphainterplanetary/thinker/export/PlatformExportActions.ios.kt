package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.pointed
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPopoverPresentationController
import platform.UIKit.UIPopoverPresentationControllerDelegateProtocol
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  IosPlatformExportActions()

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
    content: ByteArray,
  ): String? = null

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: ByteArray,
  ): Boolean = withContext(Dispatchers.Main) {
    val sourceUrl = fileUrl(internalPath) ?: return@withContext false
    val presenter = topViewController() ?: return@withContext false
    runCatching {
      // Share a temp copy named with the user-facing filename so receivers see
      // the requested name instead of the canonical UUID storage name.
      val url = exportUrlWithDisplayName(sourceUrl, filename) ?: sourceUrl
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
    content: ByteArray,
  ): Boolean = withContext(Dispatchers.Main) {
    val sourceUrl = fileUrl(internalPath) ?: return@withContext false
    val presenter = topViewController() ?: return@withContext false
    runCatching {
      val url = exportUrlWithDisplayName(sourceUrl, filename) ?: sourceUrl
      presentQuickLook(presenter, url, filename)
      true
    }.getOrDefault(false)
  }
}

internal fun fileUrl(path: String): NSURL? {
  val fm = NSFileManager.defaultManager
  if (!fm.fileExistsAtPath(path)) return null
  return NSURL.fileURLWithPath(path)
}

/**
 * Returns a file URL whose last path component is [displayFilename], copying the
 * source into a temporary location when needed so system UI shows the requested
 * name instead of the canonical UUID storage name. The canonical managed file is
 * never modified.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun exportUrlWithDisplayName(sourceUrl: NSURL, displayFilename: String): NSURL? {
  val safeName = displayFilename
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .ifBlank { sourceUrl.lastPathComponent ?: "export" }
  if (sourceUrl.lastPathComponent == safeName) return sourceUrl
  val fm = NSFileManager.defaultManager
  val tempDir = NSTemporaryDirectory().trimEnd('/') +
    "/alphathinker-export-" + NSUUID().UUIDString()
  val created = fm.createDirectoryAtPath(
    tempDir,
    withIntermediateDirectories = true,
    attributes = null,
    error = null,
  )
  if (!created) return sourceUrl
  val destination = "$tempDir/$safeName"
  val copied = fm.copyItemAtPath(sourceUrl.path ?: return sourceUrl, toPath = destination, error = null)
  return if (copied) NSURL.fileURLWithPath(destination) else sourceUrl
}

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
  val popover = controller.popoverPresentationController ?: return
  val view = presenter.view
  popover.sourceView = view
  popover.sourceRect = centeredRect(view)
  popover.permittedArrowDirections = 0u
  val delegate = ExportPopoverAnchorDelegate(view)
  PopoverDelegateRetainer.retain(delegate)
  popover.delegate = delegate
}

@OptIn(ExperimentalForeignApi::class)
private fun centeredRect(view: UIView): CValue<CGRect> =
  view.bounds.useContents {
    CGRectMake(size.width / 2.0, size.height / 2.0, 1.0, 1.0)
  }

private object PopoverDelegateRetainer {
  private val retained = mutableListOf<Any>()

  fun retain(delegate: Any) {
    retained.add(delegate)
    if (retained.size > 20) {
      retained.subList(0, retained.size - 20).clear()
    }
  }
}

/**
 * Keeps share/picker popovers centered on rotation/layout changes by updating the
 * anchor rect when UIKit repositions the popover.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ExportPopoverAnchorDelegate(
  private val anchorView: UIView,
) : NSObject(), UIPopoverPresentationControllerDelegateProtocol {

  override fun popoverPresentationController(
    popoverPresentationController: UIPopoverPresentationController,
    willRepositionPopoverToRect: CPointer<CGRect>?,
    inView: CPointer<ObjCObjectVar<UIView?>>?,
  ) {
    // 1. Update the reference view where the popover anchors from
    inView?.pointed?.value = anchorView

    // 2. Mutate the target CGRect directly in-place via .pointed
    willRepositionPopoverToRect?.pointed?.let { rect ->
      anchorView.bounds.useContents {
        rect.origin.x = size.width / 2.0
        rect.origin.y = size.height / 2.0
        rect.size.width = 1.0
        rect.size.height = 1.0
      }
    }
  }
}

private fun presentQuickLook(presenter: UIViewController, url: NSURL, displayName: String) {
  val item = ExportPreviewItem(url, displayName)
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

private class ExportPreviewItem(
  private val url: NSURL,
  private val displayName: String,
) : NSObject(), QLPreviewItemProtocol {
  override fun previewItemURL(): NSURL = url

  override fun previewItemTitle(): String = displayName
}

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
internal suspend fun presentExportPicker(filePath: String, displayFilename: String): String? =
  withContext(Dispatchers.Main) {
    val sourceUrl = fileUrl(filePath) ?: return@withContext null
    val presenter = topViewController() ?: return@withContext null
    val url = exportUrlWithDisplayName(sourceUrl, displayFilename) ?: sourceUrl
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
