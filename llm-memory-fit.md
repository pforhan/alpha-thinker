# KMP LLM Memory Heuristic Formula

To implement a model capability check directly inside a **Kotlin Multiplatform (KMP)** application, querying the specific VRAM of an unmanaged GPU across all platforms is often impractical due to native abstraction layers.

Instead, the most reliable cross-platform heuristic relies on querying **Available System RAM** and applying platform-specific constraints.

---

## 1. The Heuristic Formula

**CanRun = AvailableRAM * PlatformCoefficient > (ModelOnDiskSize + ContextBuffer)**

### Variables Explained

*   **ModelOnDiskSize**: The total size of your model files in Gigabytes (GB).
*   **ContextBuffer**: **1.5 GB** (A safe default allocation for the context window, KV cache, and basic runtime architecture).
*   **PlatformCoefficient**: A multiplier that accounts for how the host OS restricts app memory or handles shared memory architectures:

| Platform | Coefficient | Why? |
| :--- | :--- | :--- |
| **Desktop (JVM/Native)** | **0.85** | Desktop apps can freely claim almost all remaining system memory. |
| **Android (JVM)** | **0.70** | Android allows large heaps but aggressively garbage-collects or kills background processes under high pressure. |
| **iOS (Kotlin/Native)** | **0.50** | iOS has strict Jetsam memory limits. Apps are terminated if they exceed ~50% of the device's physical RAM. |
| **Web (Wasm/JS)** | **0.40** *(Max 4GB)* | Browser tabs are heavily sandboxed. Wasm (32-bit architecture) is traditionally hard-capped at a 4GB maximum layout regardless of total system RAM. |

---

## 2. KMP Architecture Setup

Define an `expect` function in your `commonMain` directory to fetch the available system RAM and platform type, then use the platform coefficients to calculate capabilities.

### `commonMain` Implementation

```kotlin
// commonMain
enum class PlatformType { DESKTOP, IOS, ANDROID, WEB }

expect fun getAvailableSystemRamGb(): Double
expect fun getPlatformType(): PlatformType

fun canRunModel(modelSizeGb: Double, contextBufferGb: Double = 1.5): Boolean {
  val availableRam = getAvailableSystemRamGb()
  val coefficient = when (getPlatformType()) {
    PlatformType.DESKTOP -> 0.85
    PlatformType.ANDROID -> 0.70
    PlatformType.IOS -> 0.50
    PlatformType.WEB -> 0.40
  }

  val usableRam = availableRam * coefficient

  // Hard cap constraint for Web Target due to 32-bit browser sandboxing
  if (getPlatformType() == PlatformType.WEB && usableRam > 4.0) {
    return 4.0 > (modelSizeGb + contextBufferGb)
  }

  return usableRam > (modelSizeGb + contextBufferGb)
}
```

---

## 3. Platform Targets (`actual`)

### Android (`androidMain`)
Queries the target system's `ActivityManager.MemoryInfo`.

```kotlin
import android.app.ActivityManager
import android.content.Context

actual fun getAvailableSystemRamGb(): Double {
  val memoryInfo = ActivityManager.MemoryInfo()
  // Assumes context provider accessibility within your KMP architecture
  val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  activityManager.getMemoryInfo(memoryInfo)
  return memoryInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
}

actual fun getPlatformType(): PlatformType = PlatformType.ANDROID
```

### iOS (`iosMain`)
Retrieves physical memory info via foundation tooling. Note that iOS manages actual availability aggressively at runtime through memory pressure warnings.

```kotlin
import platform.Foundation.NSProcessInfo

actual fun getAvailableSystemRamGb(): Double {
  val physicalMemory = NSProcessInfo.processInfo.physicalMemory
  return physicalMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)
}

actual fun getPlatformType(): PlatformType = PlatformType.IOS
```

### Desktop (`desktopMain`)
Calculates remaining available memory safely allocated to the JVM runtime environment.

```kotlin
actual fun getAvailableSystemRamGb(): Double {
  val runtime = Runtime.getRuntime()
  val maxMemory = runtime.maxMemory()
  val allocatedMemory = runtime.totalMemory()
  val freeMemory = runtime.freeMemory()
  val jvmFreeMemory = freeMemory + (maxMemory - allocatedMemory)
  return jvmFreeMemory.toDouble() / (1024.0 * 1024.0 * 1024.0)
}

actual fun getPlatformType(): PlatformType = PlatformType.DESKTOP
```

### Web (`wasmJsMain` / `jsMain`)
Web browsers heavily restrict hardware reading specifications for security and anti-fingerprinting reasons. This targets `navigator.deviceMemory` falling back to a safe 4GB baseline assumption.

```kotlin
import kotlinx.browser.window

actual fun getAvailableSystemRamGb(): Double {
  // navigator.deviceMemory returns RAM in GB rounded to the nearest power of 2 (capped at 8)
  val jsMemory = js("navigator.deviceMemory")
  return if (jsMemory != null) jsMemory.unsafeCast<Double>() else 4.0
}

actual fun getPlatformType(): PlatformType = PlatformType.WEB
```
