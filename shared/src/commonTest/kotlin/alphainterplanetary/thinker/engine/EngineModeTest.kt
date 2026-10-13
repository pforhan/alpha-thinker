package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineModeTest {

  @Test
  fun `Offline is the default mode`() {
    assertEquals(EngineMode.Offline, EngineMode.Default)
  }

  @Test
  fun `fromKey resolves known modes and ignores unknowns`() {
    assertEquals(EngineMode.Offline, EngineMode.fromKey("offline"))
    assertEquals(EngineMode.OnDevice, EngineMode.fromKey("on-device"))
    assertEquals(EngineMode.Remote, EngineMode.fromKey("remote"))
    assertEquals(EngineMode.Downloaded, EngineMode.fromKey("downloaded"))
    assertNull(EngineMode.fromKey("telepathy"))
  }

  @Test
  fun `Lite and Remote are selectable while the on-device modes are not yet`() {
    assertTrue(EngineMode.Offline.available())
    assertTrue(EngineMode.Remote.available())
    assertFalse(EngineMode.OnDevice.available())
    assertFalse(EngineMode.Downloaded.available())
  }
}