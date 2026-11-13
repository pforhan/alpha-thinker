package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.engine.config.EngineCapability
import alphainterplanetary.thinker.engine.config.EngineKind
import kotlin.test.Test
import kotlin.test.assertEquals

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

}
