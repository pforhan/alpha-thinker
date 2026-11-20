package alphainterplanetary.thinker.ui.platform

import androidx.compose.runtime.Composable

/** A file chosen by the user for portable project import. */
data class PickedImportFile(
  val name: String,
  val bytes: ByteArray,
)

/**
 * Returns a launcher that opens the platform file picker for `.thinker`
 * interchange files and delivers selected files to [onPicked].
 *
 * Returns null when file picking is unavailable on the current platform/context.
 * Implementations should support selecting one or more files where the platform
 * picker allows it.
 */
@Composable
expect fun rememberInterchangeFilePicker(
  onPicked: (List<PickedImportFile>) -> Unit,
  onMessage: (String) -> Unit,
): (() -> Unit)?
