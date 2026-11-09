package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The picker's merge and ordering (ENGINE-CONFIG-PLAN.md stage 3c): built-ins
 * and user instances land in the right [EnginePickerTier], tiers come out in display
 * order, and each tier is ordered by recency with never-used engines last.
 */
class EngineCatalogTest {

  private val ollama =
    EngineInstance(
      id = "conn-1",
      kind = EngineKind.Remote,
      provider = RemoteProvider.Ollama,
      label = "Ollama",
      baseUrl = "http://localhost:11434",
      model = "llama3",
      contextTokens = 8_192,
    )

  private val claude =
    EngineInstance(
      id = "conn-2",
      kind = EngineKind.Remote,
      provider = RemoteProvider.Anthropic,
      label = "Claude",
      baseUrl = "https://api.anthropic.com",
      apiKey = "sk-ant",
      model = "claude-sonnet-4-5",
      contextTokens = 200_000,
    )

  @Test
  fun `groups come out in tier display order`() {
    val groups = EngineCatalog.groupedByTier(listOf(ollama))

    assertEquals(
      listOf(EnginePickerTier.Offline, EnginePickerTier.Free, EnginePickerTier.Advanced),
      groups.map { it.first },
    )
  }

  @Test
  fun `built-ins land in their own tiers`() {
    val groups = EngineCatalog.groupedByTier(emptyList()).toMap()
    val preferred = listOf(EnginePickerTier.Offline, EnginePickerTier.Free)

    assertEquals(
      preferred,
      groups.keys.filter { it in preferred },
    )
    assertEquals(
      EngineRef.Builtin(EngineKind.Offline),
      groups.getValue(EnginePickerTier.Offline).single().ref,
    )
    assertEquals(
      EngineRef.Builtin(EngineKind.Remote),
      groups.getValue(EnginePickerTier.Free).single().ref,
    )
  }

  @Test
  fun `connections land under Advanced`() {
    val advanced = EngineCatalog.groupedByTier(listOf(ollama, claude))
      .first { it.first == EnginePickerTier.Advanced }
      .second

    assertEquals(
      setOf(
        EngineRef.Instance("conn-1"),
        EngineRef.Instance("conn-2"),
      ),
      advanced.map { it.ref }.toSet(),
    )
  }

  @Test
  fun `a tier is ordered most recently used first`() {
    val advanced = EngineCatalog.groupedByTier(
      instances = listOf(ollama, claude),
      lastUsedAt = mapOf(
        EngineRef.Instance("conn-1") to 1_000L,
        EngineRef.Instance("conn-2") to 2_000L,
      ),
    ).first { it.first == EnginePickerTier.Advanced }.second

    assertEquals(
      listOf(EngineRef.Instance("conn-2"), EngineRef.Instance("conn-1")),
      advanced.map { it.ref }.filterIsInstance<EngineRef.Instance>(),
    )
  }

  @Test
  fun `never-used engines keep their order behind the used ones`() {
    val advanced = EngineCatalog.groupedByTier(
      instances = listOf(ollama, claude),
      // Only the second connection has ever run. The never-used connection
      // keeps the catalog's own order behind it.
      lastUsedAt = mapOf(EngineRef.Instance("conn-2") to 2_000L),
    ).first { it.first == EnginePickerTier.Advanced }.second

    assertEquals(
      listOf(
        EngineRef.Instance("conn-2"),
        EngineRef.Instance("conn-1"),
      ),
      advanced.map { it.ref },
    )
  }

  @Test
  fun `an empty tier is dropped rather than returned empty`() {
    val groups = EngineCatalog.groupedByTier(emptyList())

    assertTrue(groups.all { it.second.isNotEmpty() })
  }
}
