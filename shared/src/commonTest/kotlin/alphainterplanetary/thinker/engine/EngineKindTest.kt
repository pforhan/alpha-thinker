package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineKindTest {

  @Test
  fun `Offline is the default kind`() {
    assertEquals(EngineKind.Offline, EngineKind.Default)
  }

  @Test
  fun `fromKey resolves known kinds and ignores retired ones`() {
    assertEquals(EngineKind.Offline, EngineKind.fromKey("offline"))
    assertEquals(EngineKind.Remote, EngineKind.fromKey("remote"))
    assertNull(EngineKind.fromKey("telepathy"))
    assertNull(EngineKind.fromKey("on-device"))
    assertNull(EngineKind.fromKey("downloaded"))
  }

  @Test
  fun `offline and Remote are both selectable`() {
    assertTrue(EngineKind.Offline.available())
    assertTrue(EngineKind.Remote.available())
  }
}
