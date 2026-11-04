package alphainterplanetary.thinker.di

import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress

/** Desktop (JVM) platform: the database is a SQLite file under the user's home directory. */
class DesktopPlatformContext : PlatformContext {
  private val home: File
    get() = File(System.getProperty("user.home"), ".alphathinker")

  override fun databaseFile(name: String): String {
    val dir = home
    dir.mkdirs()
    return File(dir, name).absolutePath
  }

  override fun modelDirectory(): String = File(home, "models").apply { mkdirs() }.absolutePath

  override fun fileExists(path: String): Boolean = File(path).isFile

  override fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }
    .getOrDefault(false) || !File(path).exists()

  override fun evalDirectory(): String = File(home, "evals").apply { mkdirs() }.absolutePath

  override fun readTextFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

  override fun writeTextFile(path: String, text: String): Boolean = runCatching {
    val target = File(path)
    target.parentFile?.mkdirs()
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

  override fun deviceInfo(): DeviceInfo {
    val os = System.getProperty("os.name").orEmpty()
    val architecture = System.getProperty("os.arch").orEmpty()
    return DeviceInfo(
      platform = "jvm",
      os = os,
      osVersion = System.getProperty("os.version"),
      device = machineName(os),
      cpu = cpuName(os) ?: architecture.ifBlank { null },
      ramGb = totalRamGb(),
    )
  }

  /** The machine model where the OS can name one, never the user running it. */
  private fun machineName(os: String): String? = runCatching {
    when {
      os.contains("mac", ignoreCase = true) -> command("sysctl", "-n", "hw.model")
      else -> InetAddress.getLocalHost().hostName
    }
  }.getOrNull()?.takeIf { it.isNotBlank() }

  private fun cpuName(os: String): String? = runCatching {
    when {
      os.contains("mac", ignoreCase = true) ->
        command("sysctl", "-n", "machdep.cpu.brand_string")

      else -> null
    }
  }.getOrNull()?.takeIf { it.isNotBlank() }

  private fun command(vararg args: String): String {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    return output.trim()
  }

  /**
   * Physical memory through the JDK's extended operating-system bean, looked up
   * by interface name so the build does not depend on a JDK-only type. Null
   * when the running JVM does not expose it.
   *
   * The method has to be read from the public interface, not from the bean's
   * own class: the concrete implementation lives in a package the JDK does not
   * export, so invoking its method fails with an access error.
   */
  private fun totalRamGb(): Double? = runCatching {
    val bean = ManagementFactory.getOperatingSystemMXBean()
    val type = Class.forName("com.sun.management.OperatingSystemMXBean")
    (type.getMethod("getTotalMemorySize").invoke(bean) as Long) / GigaByte
  }.getOrNull()

  private companion object {
    const val GigaByte = 1_073_741_824.0
  }
}
