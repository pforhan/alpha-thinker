package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelProfilesTest {
  @Test
  fun `measured seeds cover key models from evals`() {
    assertNotNull(ModelProfiles.findMeasurement("llama3.2:3b"))
    assertNotNull(ModelProfiles.findMeasurement("qwen2.5:7b"))
    assertNotNull(ModelProfiles.findMeasurement("nemotron-mini:4b"))
    assertNotNull(ModelProfiles.findMeasurement("gemma4:e4b"))
    assertNotNull(ModelProfiles.findMeasurement("deepseek-r1:1.5b"))
  }

  @Test
  fun `measured seeds have valid bands`() {
    for (seed in ModelProfiles.measuredSeeds) {
      assertTrue(seed.qualityScore >= 0.0 && seed.qualityScore <= 100.0)
      assertTrue(seed.okRate >= 0.0 && seed.okRate <= 1.0)
      assertTrue(seed.cleanRate >= 0.0 && seed.cleanRate <= 1.0)
    }
  }

  @Test
  fun `family heuristics cover common families`() {
    assertNotNull(ModelProfiles.findHeuristic("llama-3.1:8b"))
    assertNotNull(ModelProfiles.findHeuristic("qwen3:14b"))
    assertNotNull(ModelProfiles.findHeuristic("gemma3:12b"))
    assertNotNull(ModelProfiles.findHeuristic("mistral:7b"))
    assertNotNull(ModelProfiles.findHeuristic("phi3:medium"))
  }

  @Test
  fun `unknown model has no heuristic`() {
    assertNull(ModelProfiles.findHeuristic("totally-unknown-xyz:1b"))
  }
}
