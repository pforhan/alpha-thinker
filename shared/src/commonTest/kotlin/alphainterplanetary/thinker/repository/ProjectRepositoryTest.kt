package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.repository.generateTitleFromSynopsis
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectRepositoryTest {

  @Test
  fun `generateTitleFromSynopsis returns empty for empty string`() {
    assertEquals("", generateTitleFromSynopsis(""))
    assertEquals("", generateTitleFromSynopsis("   "))
  }

  @Test
  fun `generateTitleFromSynopsis returns full string when no delimiters under 30 chars`() {
    assertEquals("Short synopsis", generateTitleFromSynopsis("Short synopsis"))
    assertEquals(
      "Exactly 30 characters long!",
      generateTitleFromSynopsis("Exactly 30 characters long!")
    )
  }

  @Test
  fun `generateTitleFromSynopsis cuts at sentence end before 30 chars`() {
    assertEquals("Short", generateTitleFromSynopsis("Short. This is longer than 30 chars"))
    assertEquals("Ends with period", generateTitleFromSynopsis("Ends with period. More text here"))
  }

  @Test
  fun `generateTitleFromSynopsis cuts at newline before 30 chars`() {
    assertEquals("Line one", generateTitleFromSynopsis("Line one\nLine two continues"))
    assertEquals("First line", generateTitleFromSynopsis("First line\nSecond line"))
  }

  @Test
  fun `generateTitleFromSynopsis cuts at 30 chars when no sentence end or newline`() {
    assertEquals(
      "This is a very long synopsis w",
      generateTitleFromSynopsis("This is a very long synopsis without breaks")
    )
  }

  @Test
  fun `generateTitleFromSynopsis sentence end takes priority over 30 chars`() {
    assertEquals("A", generateTitleFromSynopsis("A. This is way longer than thirty characters"))
  }

  @Test
  fun `generateTitleFromSynopsis newline takes priority over 30 chars`() {
    assertEquals(
      "Short",
      generateTitleFromSynopsis("Short\nThis is way longer than thirty characters")
    )
  }

  @Test
  fun `generateTitleFromSynopsis sentence end takes priority over newline`() {
    assertEquals("Ends with period", generateTitleFromSynopsis("Ends with period.\nNew line here"))
  }

  @Test
  fun `generateTitleFromSynopsis trims whitespace from result`() {
    assertEquals("Hello world", generateTitleFromSynopsis("  Hello world  "))
    assertEquals("Short", generateTitleFromSynopsis("Short.\n  "))
    assertEquals("Short", generateTitleFromSynopsis("Short . \n  "))
    assertEquals("Short", generateTitleFromSynopsis("Short \n  "))
  }

  @Test
  fun `generateTitleFromSynopsis handles multiple sentences, cuts at first`() {
    assertEquals("First", generateTitleFromSynopsis("First. Second. Third."))
  }

  @Test
  fun `generateTitleFromSynopsis handles multiple newlines, cuts at first`() {
    assertEquals("Line one", generateTitleFromSynopsis("Line one\nLine two\nLine three"))
  }
}