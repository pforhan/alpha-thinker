package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineStatusTest {
  @Test
  fun liteReportsNothingActive() {
    val status = engineStatus(EngineMode.Lite, remoteBaseUrl = "http://x", remoteModel = "m")

    assertEquals(EngineCapability.entries, status.slots.map { it.capability })
    assertTrue(status.slots.all { it.state == CapabilityState.Unused })
    assertFalse(status.needsAttention)
  }

  @Test
  fun liteIgnoresTheRemoteSettings() {
    val lite = engineStatus(EngineMode.Lite)
    val remote = engineStatus(EngineMode.Remote, remoteBaseUrl = "http://x", remoteModel = "m")

    // A leftover endpoint from a previous Remote choice must not light up a
    // slot on a mode that never egresses.
    assertEquals(
      listOf(CapabilityState.Unused, CapabilityState.Unused, CapabilityState.Unused),
      lite.slots.map { it.state },
    )
    assertEquals(
      listOf(CapabilityState.Active, CapabilityState.Active, CapabilityState.Unused),
      remote.slots.map { it.state },
    )
  }

  @Test
  fun remoteCarriesEndpointAndModel() {
    val status = engineStatus(
      mode = EngineMode.Remote,
      remoteBaseUrl = "  http://localhost:11434  ",
      remoteModel = "  llama3  ",
    )

    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Network).state)
    assertEquals("http://localhost:11434", status.slot(EngineCapability.Network).detail)
    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Llm).state)
    assertEquals("llama3", status.slot(EngineCapability.Llm).detail)
    assertFalse(status.needsAttention)
  }

  @Test
  fun remoteWithoutAnEndpointIsIncompleteNotActive() {
    val status = engineStatus(mode = EngineMode.Remote, remoteModel = "llama3")

    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Llm).state)
    assertTrue(status.needsAttention)
  }

  @Test
  fun remoteWithBlankSettingsIsIncompleteInBothSlots() {
    val status = engineStatus(mode = EngineMode.Remote, remoteBaseUrl = "   ", remoteModel = "\n")

    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Llm).state)
    assertTrue(status.needsAttention)
  }

  @Test
  fun toolsStaysUnusedForEveryMode() {
    EngineMode.entries.forEach { mode ->
      val tools = engineStatus(mode, remoteBaseUrl = "http://x", remoteModel = "m")
        .slot(EngineCapability.Tools)

      assertEquals(CapabilityState.Unused, tools.state, "mode $mode")
    }
  }

  @Test
  fun onDeviceModesAreLlmOnly() {
    EngineMode.entries
      .filter { it == EngineMode.OnDevice || it == EngineMode.Downloaded }
      .forEach { mode ->
        val status = engineStatus(mode)

        assertEquals(CapabilityState.Unused, status.slot(EngineCapability.Network).state, "$mode")
        assertEquals(CapabilityState.Active, status.slot(EngineCapability.Llm).state, "$mode")
        assertEquals(mode.label, status.slot(EngineCapability.Llm).detail, "$mode")
      }
  }
}
