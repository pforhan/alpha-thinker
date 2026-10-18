package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EngineCapabilitiesTest {

  @Test
  fun `offline has no capabilities at all`() {
    assertEquals(emptySet(), EngineKind.Offline.capabilities)
  }

  @Test
  fun `remote reaches the network and runs a model`() {
    assertEquals(
      setOf(EngineCapability.Network, EngineCapability.Llm),
      EngineKind.Remote.capabilities,
    )
  }

  @Test
  fun `local model kinds run a model without reaching the network`() {
    EngineKind.entries
      .filter { it == EngineKind.OnDevice || it == EngineKind.LocalFile }
      .forEach { mode ->
        assertEquals(setOf(EngineCapability.Llm), mode.capabilities, mode.name)
      }
  }

  @Test
  fun `only remote egresses over the network`() {
    EngineKind.entries.forEach { mode ->
      assertEquals(
        mode == EngineKind.Remote,
        mode.capabilities.contains(EngineCapability.Network),
        mode.name,
      )
    }
  }

  @Test
  fun `every model-backed kind runs an llm and offline does not`() {
    EngineKind.entries.forEach { mode ->
      val expected = mode != EngineKind.Offline
      assertEquals(expected, mode.capabilities.contains(EngineCapability.Llm), mode.name)
    }
  }

  @Test
  fun `no kind calls tools until agentic lookup lands`() {
    EngineKind.entries.forEach { mode ->
      assertFalse(mode.capabilities.contains(EngineCapability.Tools), mode.name)
    }
  }
}
