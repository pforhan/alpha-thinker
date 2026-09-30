package alphainterplanetary.thinker.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

/**
 * iOS's [ClipEntry] wraps `UIPasteboard.string`, and Compose ships the text
 * factory for it.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun String.toClipEntry(): ClipEntry = ClipEntry.withPlainText(this)