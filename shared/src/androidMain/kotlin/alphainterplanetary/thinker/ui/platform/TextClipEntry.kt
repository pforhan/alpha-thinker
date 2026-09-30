package alphainterplanetary.thinker.ui.platform

import android.content.ClipData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

/**
 * Android has no text factory on [ClipEntry], so build the [ClipData] it wraps.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun String.toClipEntry(): ClipEntry =
  ClipEntry(ClipData.newPlainText(PlainTextClipLabel, this))

/** The label Android puts on the clipboard entry's toast; every app uses this. */
private const val PlainTextClipLabel = "plain text"