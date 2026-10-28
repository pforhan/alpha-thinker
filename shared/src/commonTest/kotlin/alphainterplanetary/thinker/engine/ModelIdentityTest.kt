package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelIdentityTest {

  @Test
  fun `a latest alias is resolved to the size it stands for`() {
    // `llama3.2:latest` is really `llama3.2:3b`, and the browser shows the
    // resolved name so the model can be told from its siblings.
    assertEquals("llama3.2:3b", resolveLatestAlias("llama3.2:latest", "3B"))
    assertEquals("llama3.2:7.6b", resolveLatestAlias("llama3.2:latest", "7.6B"))
  }

  @Test
  fun `a redundant zero is dropped from the size tag`() {
    assertEquals("qwen3:8b", resolveLatestAlias("qwen3:latest", "8.0B"))
  }

  @Test
  fun `an id with a real tag is left alone`() {
    assertEquals("qwen2.5:7b", resolveLatestAlias("qwen2.5:7b", "7B"))
  }

  @Test
  fun `an alias whose size is unknown keeps the alias`() {
    assertEquals("llama3.2:latest", resolveLatestAlias("llama3.2:latest", null))
    assertEquals("llama3.2:latest", resolveLatestAlias("llama3.2:latest", ""))
  }

  @Test
  fun `a descriptive label is not treated as a size`() {
    // `general.size_label` can hold a variant name such as "mini", which must
    // not be turned into a bogus tag like "minib".
    assertEquals("phi4-mini:latest", resolveLatestAlias("phi4-mini:latest", "mini"))
    assertNull(parseSizeLabel("mini"))
  }

  @Test
  fun `a size label is read in billions`() {
    assertEquals(3, parseSizeLabel("3B"))
    assertEquals(3, parseSizeLabel("3.2B"))
    assertEquals(8, parseSizeLabel("7.6B"))
    assertEquals(1, parseSizeLabel("873.44M"))
  }

  @Test
  fun `a size label the app cannot read is no size`() {
    assertNull(parseSizeLabel(null))
    assertNull(parseSizeLabel(""))
    assertNull(parseSizeLabel("many"))
  }

  @Test
  fun `an exact count is read in billions`() {
    assertEquals(3, sizeFromCount(3_212_749_824))
    assertEquals(1, sizeFromCount(873_440_000))
  }

  @Test
  fun `a count the model does not declare is no size`() {
    assertNull(sizeFromCount(null))
    assertNull(sizeFromCount(0))
  }
}
