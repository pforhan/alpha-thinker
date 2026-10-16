package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.testutil.FakePlanningEngine
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlanningEngineSelectionTest {

  private val lite = FakePlanningEngine()
  private val remote = FakePlanningEngine()

  private fun resolve(
    mode: EngineKind = EngineKind.Remote,
  ): PlanningEngine = resolveSelectedEngine(
    selectedKind = mode,
    liteEngine = lite,
    koogEngines = mapOf(EngineKind.Remote to remote),
  )

  @Test
  fun `Lite resolves the lite engine`() {
    assertSame(lite, resolve(mode = EngineKind.Offline))
  }

  @Test
  fun `a bound kind resolves its koog engine`() {
    assertSame(remote, resolve(mode = EngineKind.Remote))
  }

  @Test
  fun `an unconfigured kind throws instead of falling back`() {
    val error = assertFailsWith<IllegalStateException> { resolve(mode = EngineKind.OnDevice) }
    assertTrue(error.message.orEmpty().contains("OnDevice"), "the error names the missing kind")
  }
}