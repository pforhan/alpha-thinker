package alphainterplanetary.thinker.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringExtTest {

  @Test
  fun `jsonObject extracts a simple JSON object`() {
    val input = """{"key": "value"}"""
    assertEquals("""{"key": "value"}""", input.jsonObject())
  }

  @Test
  fun `jsonObject extracts JSON from surrounding prose`() {
    val input = "Here is the result: {\"key\": \"value\"} hope this helps!"
    assertEquals("""{"key": "value"}""", input.jsonObject())
  }

  @Test
  fun `jsonObject extracts JSON from markdown fences`() {
    val input = "```json\n{\"key\": \"value\"}\n```"
    assertEquals("""{"key": "value"}""", input.jsonObject())
  }

  @Test
  fun `jsonObject extracts only the first balanced JSON object when multiple are present`() {
    val input = "Some text {first} and {second}"
    assertEquals("{first}", input.jsonObject())
  }

  @Test
  fun `jsonObject extracts nested JSON objects correctly`() {
    val input = "Result: {\"outer\": {\"inner\": 1}} and some text"
    assertEquals("""{"outer": {"inner": 1}}""", input.jsonObject())
  }

  @Test
  fun `jsonObject returns null when no opening brace is present`() {
    val input = "just some text"
    assertNull(input.jsonObject())
  }

  @Test
  fun `jsonObject returns null when no closing brace is present`() {
    val input = "{\"key\": \"value\""
    assertNull(input.jsonObject())
  }

  @Test
  fun `jsonObject returns null when braces are in wrong order`() {
    val input = "} some text {"
    assertNull(input.jsonObject())
  }

  @Test
  fun `jsonObject returns a value even if preceded by a closing brace`() {
    val input = "} some text {jsonhere}"
    assertEquals("{jsonhere}", input.jsonObject())
  }

  @Test
  fun `jsonObject returns null when only one brace is present`() {
    val input = "{"
    assertNull(input.jsonObject())
  }

  @Test
  fun `jsonObject returns null when string is empty`() {
    assertNull("".jsonObject())
  }
}
