package alphainterplanetary.thinker.engine

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.modelsById
import alphainterplanetary.thinker.engine.config.RemoteProvider
import alphainterplanetary.thinker.engine.config.normalizeRemoteBaseUrl
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
  fun `suggested connection names are short and human`() {
    // What a connection is called in the user's list, prefetched into the
    // dialog's Name field: the local server and the vendor keep their names,
    // and the generic /v1 escape hatch is what it is to the user — an API —
    // rather than the protocol chip it was picked from.
    assertEquals("Ollama", RemoteProvider.Ollama.suggestedConnectionName)
    assertEquals("API", RemoteProvider.OpenAiCompatible.suggestedConnectionName)
    assertEquals("Anthropic", RemoteProvider.Anthropic.suggestedConnectionName)
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

  @Test
  fun `an endpoint keeps its explicit scheme`() {
    assertEquals(
      "https://alpha.tail2dd774.ts.net",
      normalizeRemoteBaseUrl("https://alpha.tail2dd774.ts.net"),
    )
    assertEquals(
      "http://localhost:11434",
      normalizeRemoteBaseUrl("http://localhost:11434"),
    )
  }

  @Test
  fun `a scheme-less endpoint is given the scheme its host implies`() {
    // Without a scheme Ktor parses the host as a path and sends the request to
    // localhost — the bug behind "it always talks to localhost". A public host
    // is assumed TLS-terminated; loopback and LAN names are plain HTTP.
    assertEquals(
      "https://alpha.tail2dd774.ts.net",
      normalizeRemoteBaseUrl(" alpha.tail2dd774.ts.net "),
    )
    assertEquals(
      "http://localhost:11434",
      normalizeRemoteBaseUrl("localhost:11434"),
    )
    assertEquals(
      "http://127.0.0.1:11434",
      normalizeRemoteBaseUrl("127.0.0.1:11434"),
    )
    assertEquals(
      "http://acerswitch.local:11435",
      normalizeRemoteBaseUrl("acerswitch.local:11435"),
    )
    assertEquals(
      "http://192.168.72.120:11435",
      normalizeRemoteBaseUrl("192.168.72.120:11435"),
    )
  }

  @Test
  fun `the trailing slash and a v1 root are trimmed in either order`() {
    assertEquals(
      "https://example.com",
      normalizeRemoteBaseUrl("https://example.com/"),
    )
    assertEquals(
      "https://example.com",
      normalizeRemoteBaseUrl("https://example.com/v1"),
    )
    assertEquals(
      "https://example.com",
      normalizeRemoteBaseUrl(" https://example.com/v1/ "),
    )
    assertEquals("", normalizeRemoteBaseUrl("   "))
  }
}
