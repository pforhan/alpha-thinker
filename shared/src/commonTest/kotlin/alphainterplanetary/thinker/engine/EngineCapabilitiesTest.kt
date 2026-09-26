package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EngineCapabilitiesTest {

  @Test
  fun `lite has no capabilities at all`() {
    assertEquals(emptySet(), EngineMode.Lite.capabilities)
  }

  @Test
  fun `remote reaches the network and runs a model`() {
    assertEquals(
      setOf(EngineCapability.Network, EngineCapability.Llm),
      EngineMode.Remote.capabilities,
    )
  }

  @Test
  fun `local model modes run a model without reaching the network`() {
    EngineMode.entries
      .filter { it == EngineMode.OnDevice || it == EngineMode.Downloaded }
      .forEach { mode ->
        assertEquals(setOf(EngineCapability.Llm), mode.capabilities, mode.name)
      }
  }

  @Test
  fun `only remote egresses over the network`() {
    EngineMode.entries.forEach { mode ->
      assertEquals(
        mode == EngineMode.Remote,
        mode.capabilities.contains(EngineCapability.Network),
        mode.name,
      )
    }
  }

  @Test
  fun `every model-backed mode runs an llm and lite does not`() {
    EngineMode.entries.forEach { mode ->
      val expected = mode != EngineMode.Lite
      assertEquals(expected, mode.capabilities.contains(EngineCapability.Llm), mode.name)
    }
  }

  @Test
  fun `no mode calls tools until agentic lookup lands`() {
    EngineMode.entries.forEach { mode ->
      assertFalse(mode.capabilities.contains(EngineCapability.Tools), mode.name)
    }
  }
}
