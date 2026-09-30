package alphainterplanetary.thinker.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

/**
 * A [StringSelection] is already a `Transferable`, which is exactly what
 * `ClipEntry.asAwtTransferable` unwraps on the way to the system clipboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun String.toClipEntry(): ClipEntry = ClipEntry(StringSelection(this))