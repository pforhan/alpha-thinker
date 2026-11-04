package alphainterplanetary.thinker.di

/**
 * Best-effort description of the machine the app is running on.
 *
 * Evaluation rows carry this so a result measured on a phone can never be
 * pooled with the same model measured on a laptop. Every field is nullable
 * because a platform may not be able to answer it, and a missing field is
 * better than a guessed one that would merge two different devices.
 */
data class DeviceInfo(
  val platform: String? = null,
  val os: String? = null,
  val osVersion: String? = null,
  val device: String? = null,
  val cpu: String? = null,
  val ramGb: Double? = null,
  val appVersion: String? = null,
  val apiLevel: Int? = null,
) {
  /**
   * A stable grouping key for the hardware, built from the fields above. Lower
   * case and non-alphanumeric characters collapsed so the same device reports
   * the same key across launches and platforms.
   */
  fun key(): String = listOfNotNull(
    platform,
    os,
    osVersion,
    device,
    cpu,
    ramGb?.let { "${it}GB" },
  ).joinToString("|")
    .lowercase()
    .replace(Regex("[^a-z0-9|.]+"), "_")
    .trim('_')
    .ifBlank { "unknown" }
}
