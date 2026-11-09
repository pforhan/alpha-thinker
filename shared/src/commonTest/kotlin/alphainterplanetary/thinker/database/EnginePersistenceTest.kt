package alphainterplanetary.thinker.database

import kotlin.test.Test
import kotlin.test.assertNull

class EnginePersistenceTest {
  @Test
  fun `a stored instance with a retired kind maps to null instead of crashing`() {
    assertNull(retiredRow("on-device").toModel())
    assertNull(retiredRow("downloaded").toModel())
  }

  private fun retiredRow(kind: String) = EngineInstanceEntity(
    id = "legacy-$kind",
    kind = kind,
    provider = null,
    label = "Legacy",
    baseUrl = "",
    apiKey = "",
    model = "",
    contextTokens = 0,
  )
}
