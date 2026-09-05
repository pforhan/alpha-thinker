package alphainterplanetary.thinker.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class SeedQuestionsGeneratorTest {

  private val generator = SeedQuestionsGenerator()

  @Test
  fun `recommendTitle returns empty for empty string`() {
    assertEquals("", title(""))
    assertEquals("", title("   "))
  }

  @Test
  fun `recommendTitle returns full string when no delimiters under 30 chars`() {
    assertEquals("Short synopsis", title("Short synopsis"))
    assertEquals(
      "Exactly 30 characters long!",
      title("Exactly 30 characters long!")
    )
  }

  @Test
  fun `recommendTitle cuts at sentence end before 30 chars`() {
    assertEquals("Short", title("Short. This is longer than 30 chars"))
    assertEquals("Ends with period", title("Ends with period. More text here"))
  }

  @Test
  fun `recommendTitle cuts at newline before 30 chars`() {
    assertEquals("Line one", title("Line one\nLine two continues"))
    assertEquals("First line", title("First line\nSecond line"))
  }

  @Test
  fun `recommendTitle cuts at 30 chars when no sentence end or newline`() {
    assertEquals(
      "This is a very long synopsis w",
      title("This is a very long synopsis without breaks")
    )
  }

  @Test
  fun `recommendTitle sentence end takes priority over 30 chars`() {
    assertEquals("A", title("A. This is way longer than thirty characters"))
  }

  @Test
  fun `recommendTitle newline takes priority over 30 chars`() {
    assertEquals(
      "Short",
      title("Short\nThis is way longer than thirty characters")
    )
  }

  @Test
  fun `recommendTitle sentence end takes priority over newline`() {
    assertEquals("Ends with period", title("Ends with period.\nNew line here"))
  }

  @Test
  fun `recommendTitle trims whitespace from result`() {
    assertEquals("Hello world", title("  Hello world  "))
    assertEquals("Short", title("Short.\n  "))
    assertEquals("Short", title("Short . \n  "))
    assertEquals("Short", title("Short \n  "))
  }

  @Test
  fun `recommendTitle handles multiple sentences, cuts at first`() {
    assertEquals("First", title("First. Second. Third."))
  }

  @Test
  fun `recommendTitle handles multiple newlines, cuts at first`() {
    assertEquals("Line one", title("Line one\nLine two\nLine three"))
  }

  private fun title(synopsis: String): String = generator.generateTitleFromSynopsisForTest(synopsis)
}
