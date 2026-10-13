package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.modelsById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteProviderTest {

  @Test
  fun `the default provider is the local Ollama target and is listed first`() {
    // A fresh install starts on a local Ollama server — the app's documented
    // edge target, which needs no key and can be asked for its own models — and
    // the picker lists it first so the default and the leading option agree.
    assertEquals(RemoteProvider.Ollama, RemoteProvider.Default)
    assertEquals(RemoteProvider.Default, RemoteProvider.entries.first())
  }

  @Test
  fun `keys are unique and round-trip`() {
    val keys = RemoteProvider.entries.map { it.key }

    assertEquals(keys.size, keys.toSet().size)
    RemoteProvider.entries.forEach { provider ->
      assertEquals(provider, RemoteProvider.fromKey(provider.key))
    }
  }

  @Test
  fun `an unknown key resolves to nothing`() {
    assertNull(RemoteProvider.fromKey("not-a-provider"))
  }

  @Test
  fun `every provider names a starting model and endpoint`() {
    RemoteProvider.entries.forEach { provider ->
      assertTrue(provider.defaultModel.isNotBlank(), "$provider model")
      assertTrue(provider.defaultBaseUrl.isNotBlank(), "$provider endpoint")
      assertTrue(provider.defaultContextTokens > 0, "$provider window")
    }
  }

  @Test
  fun `only the providers that cannot send without a key require one`() {
    assertTrue(RemoteProvider.Anthropic.requiresApiKey)
    // A key field the provider never reads would be pure confusion.
    assertTrue(!RemoteProvider.OpenAiCompatible.requiresApiKey)
    assertTrue(!RemoteProvider.Ollama.requiresApiKey)
    // A keyed provider points at where to make the key.
    assertNotNull(RemoteProvider.Anthropic.apiKeyUrl)
    assertNull(RemoteProvider.Ollama.apiKeyUrl)
  }

  @Test
  fun `the anthropic starter model is still in koogs catalogue`() {
    // The backend resolves this id against AnthropicModels to pick up the
    // model's capabilities and window; a rename upstream would silently drop
    // the default to a capability-only custom model.
    val catalogue = AnthropicModels.modelsById()
    val model = catalogue[RemoteProvider.Anthropic.defaultModel]

    assertNotNull(model, "catalogue no longer carries ${RemoteProvider.Anthropic.defaultModel}")
  }

  @Test
  fun `the anthropic starter window matches the catalogue model`() {
    val catalogue = AnthropicModels.modelsById()
    val model = catalogue.getValue(RemoteProvider.Anthropic.defaultModel)

    assertEquals(model.contextLength?.toInt(), RemoteProvider.Anthropic.defaultContextTokens)
  }

  @Test
  fun `the anthropic endpoint is fixed and the others are configurable`() {
    assertTrue(!RemoteProvider.Anthropic.configurableBaseUrl)
    assertTrue(RemoteProvider.Ollama.configurableBaseUrl)
    assertTrue(RemoteProvider.OpenAiCompatible.configurableBaseUrl)
  }
}
