package alphainterplanetary.thinker.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UtilsTest {

  @Test
  fun `randomUUID generates a valid v4 UUID`() {
    val uuid = randomUUID()

    assertTrue(
      Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(uuid),
      "unexpected UUID format: $uuid",
    )
  }

  @Test
  fun `randomUUID generates unique values`() {
    val values = List(100) { randomUUID() }

    assertEquals(100, values.toSet().size)
  }

  @Test
  fun `randomUUID output is well-formed`() {
    val uuid = randomUUID()

    assertFalse(uuid.startsWith("-"), "must not start with a dash")
    assertEquals(4, uuid.count { it == '-' }, "must contain exactly 4 dashes")
    assertNotEquals("00000000-0000-4000-8000-000000000000", uuid)
  }

  @Test
  fun `now returns an instant`() {
    val before = now()
    val value = now()
    val after = now()

    assertTrue(before <= value)
    assertTrue(value <= after)
  }
}