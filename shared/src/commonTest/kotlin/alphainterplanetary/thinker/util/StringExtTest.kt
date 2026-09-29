package alphainterplanetary.thinker.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringExtTest {

  @Test
  fun `jsonArray extracts a simple JSON array`() {
    assertEquals("""["a", "b"]""", """["a", "b"]""".jsonArray())
  }

  @Test
  fun `jsonArray extracts JSON from surrounding prose and fences`() {
    val input = "Here you go:\n```json\n[\"a\"]\n```\nHope that helps!"
    assertEquals("""["a"]""", input.jsonArray())
  }

  @Test
  fun `jsonArray extracts only the first balanced array when multiple are present`() {
    assertEquals("""[1]""", "See [1] and [2]".jsonArray())
  }

  @Test
  fun `jsonArray extracts nested arrays correctly`() {
    val input = """Result: [[1, 2], [3]] and the rest"""
    assertEquals("""[[1, 2], [3]]""", input.jsonArray())
  }

  @Test
  fun `jsonArray ignores brackets inside JSON strings`() {
    val input = """["What about [draft]?"]"""
    assertEquals(input, input.jsonArray())
  }

  @Test
  fun `jsonArray extracts a value even if preceded by a closing bracket`() {
    assertEquals("""[a]""", "} some text [a]".jsonArray())
  }

  @Test
  fun `jsonArray returns null when no opening bracket is present`() {
    assertNull("just some text".jsonArray())
  }

  @Test
  fun `jsonArray returns null when no closing bracket is present`() {
    assertNull("""["a", "b" """.jsonArray())
  }

  @Test
  fun `jsonArray returns null when only one bracket is present`() {
    assertNull("[".jsonArray())
  }

  @Test
  fun `jsonArray returns null when string is empty`() {
    assertNull("".jsonArray())
  }

  @Test
  fun `jsonArray does not match a JSON object`() {
    assertNull("""{"a": 1}""".jsonArray())
  }
}
