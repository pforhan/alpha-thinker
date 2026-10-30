package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRankerTest {
  private fun info(
    id: String,
    displayName: String = id,
    source: ModelSource = ModelSource.Ollama,
  ) = RemoteModelInfo(
    id = id,
    source = source,
    displayName = displayName,
  )

  @Test
  fun `measured strong and fast model is recommended`() {
    val ranks = ModelRanker.rank(listOf(info("llama3.2:3b", "llama3.2:3b")))
    assertEquals(1, ranks.size)
    assertEquals(ModelPresentationTier.Recommended, ranks[0].presentationTier)
    assertEquals(ModelQuality.Strong, ranks[0].quality)
    assertEquals(ModelSpeed.Fast, ranks[0].speed)
    assertFalse(ranks[0].estimated)
  }

  @Test
  fun `measured strong and fine model is high quality but slower`() {
    val ranks = ModelRanker.rank(listOf(info("qwen2.5:7b")))
    assertEquals(ModelPresentationTier.HighQualityButSlower, ranks[0].presentationTier)
    assertEquals(ModelQuality.Strong, ranks[0].quality)
    assertEquals(ModelSpeed.Fine, ranks[0].speed)
  }

  @Test
  fun `measured adequate and fast model is lower quality but fast`() {
    val ranks = ModelRanker.rank(listOf(info("nemotron-mini:4b")))
    assertEquals(ModelPresentationTier.LowerQualityButFast, ranks[0].presentationTier)
    assertEquals(ModelQuality.Adequate, ranks[0].quality)
    assertEquals(ModelSpeed.Fast, ranks[0].speed)
  }

  @Test
  fun `measured strong but slow model is high quality but slower`() {
    val ranks = ModelRanker.rank(listOf(info("gemma4:e4b")))
    assertEquals(ModelPresentationTier.HighQualityButSlower, ranks[0].presentationTier)
    assertEquals(ModelQuality.Strong, ranks[0].quality)
    assertEquals(ModelSpeed.Slow, ranks[0].speed)
  }

  @Test
  fun `measured weak model is discouraged`() {
    val ranks = ModelRanker.rank(listOf(info("qwen2.5:1.5b")))
    assertEquals(ModelPresentationTier.Discouraged, ranks[0].presentationTier)
    assertEquals(ModelQuality.Weak, ranks[0].quality)
  }

  @Test
  fun `unmeasured model uses family heuristics marked estimated`() {
    val ranks = ModelRanker.rank(listOf(info("llama-3.1:8b")))
    assertEquals(ModelPresentationTier.HighQualityButSlower, ranks[0].presentationTier)
    assertEquals(ModelQuality.Strong, ranks[0].quality)
    assertTrue(ranks[0].estimated)
    assertTrue(ranks[0].reason.contains("(estimated)"))
  }

  @Test
  fun `unrecognized unmeasured model is unknown`() {
    val ranks = ModelRanker.rank(listOf(info("some-random:model")))
    assertEquals(ModelPresentationTier.Unknown, ranks[0].presentationTier)
    assertEquals(ModelQuality.Unknown, ranks[0].quality)
    assertEquals(ModelSpeed.Unknown, ranks[0].speed)
    assertTrue(ranks[0].estimated)
  }

  @Test
  fun `models are sorted by presentation tier then quality then speed`() {
    val ranks = ModelRanker.rank(
      listOf(
        info("qwen2.5:1.5b"), // discouraged, weak
        info("llama3.2:3b"), // recommended
        info("qwen2.5:7b"),  // high quality but slower
        info("llama-3.1:8b"), // high quality but slower (heuristic, strong/fine)
      ),
    )
    assertEquals(ModelPresentationTier.Recommended, ranks[0].presentationTier)
    assertEquals(ModelPresentationTier.HighQualityButSlower, ranks[1].presentationTier)
    assertEquals(ModelPresentationTier.HighQualityButSlower, ranks[2].presentationTier)
    assertEquals(ModelPresentationTier.Discouraged, ranks[3].presentationTier)
  }

  @Test
  fun `measured model matched by display name`() {
    val ranks = ModelRanker.rank(listOf(info("llama3.2:latest", displayName = "llama3.2:3b")))
    assertEquals(ModelPresentationTier.Recommended, ranks[0].presentationTier)
    assertFalse(ranks[0].estimated)
  }

  @Test
  fun `discouraged models sort adequate quality before weak quality`() {
    val ranks = ModelRanker.rank(
      listOf(
        info("gemma4:12b"), // discouraged, weak, slow
        info("gemma4:26b"), // discouraged, adequate, slow
        info("qwen2.5:1.5b"), // discouraged, weak, fast
      ),
    )
    assertTrue(ranks.all { it.presentationTier == ModelPresentationTier.Discouraged })
    // Adequate first, then weak fastest-first.
    assertEquals(
      listOf("gemma4:26b", "qwen2.5:1.5b", "gemma4:12b"),
      ranks.map { it.model.id },
    )
  }
}
