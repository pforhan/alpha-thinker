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
    assertEquals(EngineKind.Remote, EngineKind.fromKey("remote"))
    assertEquals(EngineKind.LocalFile, EngineKind.fromKey("downloaded"))
    assertNull(EngineKind.fromKey("telepathy"))
    assertNull(EngineKind.fromKey("on-device"))
  }

  @Test
  fun `offline and Remote are selectable while the local runtime is absent`() {
    assertTrue(EngineKind.Offline.available())
    assertTrue(EngineKind.Remote.available())
    assertFalse(EngineKind.LocalFile.available())
  }
}
