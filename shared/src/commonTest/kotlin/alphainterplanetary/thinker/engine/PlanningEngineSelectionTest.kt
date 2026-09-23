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
    mode: EngineMode = EngineMode.Remote,
    llmEnabled: Boolean = true,
  ): PlanningEngine = resolveSelectedEngine(
    selectedMode = mode,
    llmEnabled = llmEnabled,
    liteEngine = lite,
    koogEngines = mapOf(EngineMode.Remote to remote),
  )

  @Test
  fun `LLM off resolves the lite engine regardless of the selected mode`() {
    assertSame(lite, resolve(mode = EngineMode.Remote, llmEnabled = false))
  }

  @Test
  fun `Lite mode resolves the lite engine`() {
    assertSame(lite, resolve(mode = EngineMode.Lite))
  }

  @Test
  fun `a bound mode resolves its koog engine`() {
    assertSame(remote, resolve(mode = EngineMode.Remote))
  }

  @Test
  fun `an unconfigured mode throws instead of falling back`() {
    val error = assertFailsWith<IllegalStateException> { resolve(mode = EngineMode.OnDevice) }
    assertTrue(error.message.orEmpty().contains("OnDevice"), "the error names the missing mode")
  }
}