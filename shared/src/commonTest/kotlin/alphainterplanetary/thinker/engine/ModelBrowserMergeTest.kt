package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelBrowserMergeTest {

  private fun installed(vararg ids: String): List<RemoteModelInfo> =
    ids.map { RemoteModelInfo(id = it, source = ModelSource.Ollama) }

  @Test
  fun `curated strong models that are missing become pull entries`() {
    val merged = ModelBrowserMerge.merge(emptyList())
    val pulls = merged.filter { it.isPull }
    // At least the curated strong seeds not installed are pulls.
    assertTrue(pulls.isNotEmpty())
    assertEquals(ModelProfiles.strongModelIds.size, pulls.size)
  }

  @Test
  fun `installed ids are not duplicated as pull chips`() {
    val first = ModelProfiles.strongModelIds.first()
    val merged = ModelBrowserMerge.merge(installed(first))
    assertFalse(merged.any { it.isPull && it.rank.model.id == first })
  }

  @Test
  fun `a latest alias is recognized as the same model as its curated tag`() {
    val first = ModelProfiles.strongModelIds.first()
    // Simulate an installed `:latest` alias that resolves to the curated tag.
    val alias = RemoteModelInfo(
      id = first.substringBefore(':') + ":latest",
      source = ModelSource.Ollama,
      displayName = first,
    )
    val merged = ModelBrowserMerge.merge(listOf(alias))
    assertFalse(
      merged.any { it.isPull && it.rank.model.id == first },
      "installed alias should suppress its curated pull chip",
    )
  }

  @Test
  fun `non ollama providers produce no pull chips`() {
    val merged = ModelBrowserMerge.merge(
      installed("foo"),
      provider = RemoteProvider.Anthropic,
    )
    assertTrue(merged.none { it.isPull })
  }

  @Test
  fun `merged entries keep installed entries present`() {
    val id = "custom-model"
    val merged = ModelBrowserMerge.merge(installed(id), provider = RemoteProvider.Ollama)
    assertTrue(merged.any { it.rank.model.id == id && !it.isPull })
  }

  @Test
  fun `recommended tier comes before discouraged`() {
    val merged = ModelBrowserMerge.merge(emptyList())
    val tiers = merged.map { it.rank.presentationTier }
    val firstRec = tiers.indexOfFirst { it == ModelPresentationTier.Recommended }
    val firstDisc = tiers.indexOfFirst { it == ModelPresentationTier.Discouraged }
    if (firstRec >= 0 && firstDisc >= 0) {
      assertTrue(firstRec < firstDisc)
    }
  }
}
