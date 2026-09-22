package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineModeTest {

  @Test
  fun `Lite is the default mode`() {
    assertEquals(EngineMode.Lite, EngineMode.Default)
  }

  @Test
  fun `fromKey resolves known modes and ignores unknowns`() {
    assertEquals(EngineMode.Lite, EngineMode.fromKey("lite"))
    assertEquals(EngineMode.OnDevice, EngineMode.fromKey("on-device"))
    assertEquals(EngineMode.Remote, EngineMode.fromKey("remote"))
    assertEquals(EngineMode.Downloaded, EngineMode.fromKey("downloaded"))
    assertNull(EngineMode.fromKey("telepathy"))
  }

  @Test
  fun `only Lite is selectable until its backend ships`() {
    assertTrue(EngineMode.Lite.available())
    assertFalse(EngineMode.OnDevice.available())
    assertFalse(EngineMode.Remote.available())
    assertFalse(EngineMode.Downloaded.available())
  }
}