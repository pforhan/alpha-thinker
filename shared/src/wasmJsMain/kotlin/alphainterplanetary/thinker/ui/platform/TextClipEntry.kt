package alphainterplanetary.thinker.ui.platform

import androidx.compose.ui.platform.ClipEntry

/**
 * [ClipEntry.withPlainText] builds a `ClipboardItem` from a `Blob`, falling back
 * to `Clipboard.writeText` where the async Clipboard API isn't available.
 */
internal actual fun String.toClipEntry(): ClipEntry = ClipEntry.withPlainText(this)