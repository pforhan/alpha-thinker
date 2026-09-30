package alphainterplanetary.thinker.ui.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

/**
 * This text as a platform clipboard entry, ready for `Clipboard.setClipEntry`.
 *
 * The modern clipboard API can't be called from shared code: [ClipEntry]'s
 * constructor is `expect`ed but has no common `actual`, so each target takes
 * something different — a `ClipData` on Android, a `Transferable` on desktop, a
 * `JsArray<ClipboardItem>` on wasm, nothing at all on iOS. The text factory that
 * would paper over that ([ClipEntry.withPlainText]) exists on js, wasm and iOS
 * but not on Android or desktop, so it can't be the shared call either. This
 * seam is the one place that has to know the difference; the deprecated
 * `LocalClipboardManager` is the alternative, and the worse of the two.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal expect fun String.toClipEntry(): ClipEntry