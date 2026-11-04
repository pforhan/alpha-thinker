package alphainterplanetary.thinker.di

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

class AndroidPlatformContext(androidContext: Context) : PlatformContext {
  private val context: Context = androidContext.applicationContext

  override fun databaseFile(name: String): String = context.getDatabasePath(name).absolutePath

  /**
   * Internal app storage, not external: downloaded models must survive a USB
   * unmount and must not need a storage permission. The directory is created on
   * first read because the download client writes into it directly.
   */
  override fun modelDirectory(): String = File(context.filesDir, "models").apply { mkdirs() }.absolutePath

  override fun fileExists(path: String): Boolean = File(path).isFile

  override fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }
    .getOrDefault(false) || !File(path).exists()

  /**
   * Evaluation rows live beside the models but outside the model directory, so
   * clearing downloads can never take the measurement history with it.
   */
  override fun evalDirectory(): String = File(context.filesDir, "evals").apply { mkdirs() }.absolutePath

  override fun readTextFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

  override fun writeTextFile(path: String, text: String): Boolean = runCatching {
    val target = File(path)
    target.parentFile?.mkdirs()
    // Write-then-rename so a process killed while a row was flushing leaves the
    // previous complete row rather than a truncated JSON file.
    val temporary = File("${target.absolutePath}.tmp")
    temporary.writeText(text)
    temporary.renameTo(target) || run {
      target.writeText(text)
      temporary.delete()
      true
    }
  }.getOrDefault(false)

  override fun listFiles(directory: String): List<String> = runCatching {
    File(directory).listFiles()
      ?.filter { it.isFile }
      ?.map { it.name }
      .orEmpty()
  }.getOrDefault(emptyList())

  @Suppress("DEPRECATION")
  override fun deviceInfo(): DeviceInfo {
    val memory = runCatching {
      val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      val info = ActivityManager.MemoryInfo()
      manager.getMemoryInfo(info)
      info.totalMem / GigaByte
    }.getOrNull()
    val versionName = runCatching {
      context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null
    return DeviceInfo(
      platform = "android",
      os = "Android",
      osVersion = Build.VERSION.RELEASE,
      device = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .replaceFirstChar { it.uppercase() }
        .ifBlank { null },
      cpu = listOfNotNull(soc?.takeIf { it != Build.UNKNOWN }, Build.HARDWARE)
        .joinToString(" · ")
        .ifBlank { null },
      ramGb = memory,
      appVersion = versionName,
      apiLevel = Build.VERSION.SDK_INT,
    )
  }

  private companion object {
    const val GigaByte = 1_073_741_824.0
  }
}
