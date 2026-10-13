package alphainterplanetary.thinker.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenFormatTest {

  @Test
  fun `formatTokens shows counts under a thousand as they are`() {
    assertEquals("0", formatTokens(0))
    assertEquals("1", formatTokens(1))
    assertEquals("999", formatTokens(999))
  }

  @Test
  fun `formatTokens switches to k at a thousand`() {
    assertEquals("1k", formatTokens(1_000))
  }

  @Test
  fun `formatTokens floors thousands to a whole number`() {
    assertEquals("2k", formatTokens(2_048))
    assertEquals("8k", formatTokens(8_192))
    assertEquals("131k", formatTokens(131_072))
    assertEquals("999k", formatTokens(999_999))
  }

  @Test
  fun `formatTokens switches to m at a million`() {
    assertEquals("1m", formatTokens(1_000_000))
  }

  @Test
  fun `formatTokens floors millions to a whole number`() {
    assertEquals("1m", formatTokens(1_999_999))
    assertEquals("2m", formatTokens(2_000_000))
  }
}
