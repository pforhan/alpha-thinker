package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineKindTest {

  @Test
  fun `Offline is the default kind`() {
    assertEquals(EngineKind.Offline, EngineKind.Default)
  }

  @Test
  fun `fromKey resolves known kinds and ignores unknowns`() {
    assertEquals(EngineKind.Offline, EngineKind.fromKey("offline"))
    assertEquals(EngineKind.OnDevice, EngineKind.fromKey("on-device"))
    assertEquals(EngineKind.Remote, EngineKind.fromKey("remote"))
    assertEquals(EngineKind.LocalFile, EngineKind.fromKey("downloaded"))
    assertNull(EngineKind.fromKey("telepathy"))
  }

  @Test
  fun `offline and Remote are selectable while the on-device kinds are not yet`() {
    assertTrue(EngineKind.Offline.available())
    assertTrue(EngineKind.Remote.available())
    assertFalse(EngineKind.OnDevice.available())
    assertFalse(EngineKind.LocalFile.available())
  }
}
