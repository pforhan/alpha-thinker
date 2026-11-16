package alphainterplanetary.thinker.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenEstimatorTest {

  @Test
  fun `an estimate is the character count at four chars per token rounded up`() {
    assertEquals(0, TokenEstimator.estimateTokens(""))
    assertEquals(1, TokenEstimator.estimateTokens("abcd"))
    assertEquals(2, TokenEstimator.estimateTokens("abcde"))
    assertEquals(25, TokenEstimator.estimateTokens("x".repeat(100)))
  }
}
