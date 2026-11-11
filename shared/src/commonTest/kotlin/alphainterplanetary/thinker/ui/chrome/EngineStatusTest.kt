package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.config.EngineAvailability.Available
import alphainterplanetary.thinker.engine.config.EngineAvailability.Unavailable
import alphainterplanetary.thinker.engine.config.EngineCapability
import alphainterplanetary.thinker.engine.config.EngineInstance
import alphainterplanetary.thinker.engine.config.EngineKind
import alphainterplanetary.thinker.engine.config.EngineRef
import alphainterplanetary.thinker.engine.config.RemoteProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineStatusTest {

  private fun remoteInstance(
    provider: RemoteProvider = RemoteProvider.Ollama,
    baseUrl: String = "http://localhost:11434",
    apiKey: String = "",
    model: String = "llama3",
  ) = EngineInstance(
    id = "conn-1",
    kind = EngineKind.Remote,
    provider = provider,
    label = "Test connection",
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
  )

  @Test
  fun builtinOfflineReportsEverySlotUnused() {
    val status = engineStatus(EngineRef.Builtin(EngineKind.Offline), availability = Available)

    assertEquals(EngineCapability.entries, status.slots.map { it.capability })
    assertTrue(status.slots.all { it.readiness == CapabilityReadiness.Unused })
    assertFalse(status.needsAttention)
  }

  @Test
  fun offlineStaysUnusedEvenWhenAnInstanceIsSupplied() {
    // A leftover connection from a previous choice must not light up a slot on
    // an engine that never egresses.
    val status = engineStatus(
      ref = EngineRef.Builtin(EngineKind.Offline),
      instance = remoteInstance(),
      availability = Available,
    )

    assertTrue(status.slots.all { it.readiness == CapabilityReadiness.Unused })
    assertFalse(status.needsAttention)
  }

  @Test
  fun completeRemoteInstanceIsActiveWithDetailsFromItsFields() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(baseUrl = "  http://localhost:11434  ", model = "  llama3  "),
      availability = Available,
    )

    assertEquals(CapabilityReadiness.Active, status.slot(EngineCapability.Network).readiness)
    assertEquals("http://localhost:11434", status.slot(EngineCapability.Network).detail)
    assertEquals(CapabilityReadiness.Active, status.slot(EngineCapability.Llm).readiness)
    assertEquals("llama3", status.slot(EngineCapability.Llm).detail)
    assertFalse(status.needsAttention)
  }

  @Test
  fun remoteWithoutAnEndpointIsIncompleteNotActive() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(baseUrl = ""),
      availability = Available,
    )

    assertEquals(CapabilityReadiness.Incomplete, status.slot(EngineCapability.Network).readiness)
    assertEquals(CapabilityReadiness.Active, status.slot(EngineCapability.Llm).readiness)
    assertTrue(status.needsAttention)
  }

  @Test
  fun remoteWithBlankFieldsIsIncompleteInBothSlots() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(baseUrl = "   ", model = "\n"),
      availability = Available,
    )

    assertEquals(CapabilityReadiness.Incomplete, status.slot(EngineCapability.Network).readiness)
    assertEquals(CapabilityReadiness.Incomplete, status.slot(EngineCapability.Llm).readiness)
    assertTrue(status.needsAttention)
  }

  @Test
  fun keyedProviderWithoutAKeyIsIncompleteEvenAtItsFixedOrigin() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(
        provider = RemoteProvider.Anthropic,
        baseUrl = "",
        apiKey = "",
        model = "claude-sonnet-4-5",
      ),
      availability = Available,
    )

    assertEquals(CapabilityReadiness.Incomplete, status.slot(EngineCapability.Network).readiness)
    assertTrue(status.needsAttention)
  }

  @Test
  fun keyedProviderShowsItsFixedOriginOnceKeyed() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(
        provider = RemoteProvider.Anthropic,
        // Stale from a previous provider: a fixed origin ignores the stored field.
        baseUrl = "http://localhost:11434",
        apiKey = "sk-ant",
        model = "claude-sonnet-4-5",
      ),
      availability = Available,
    )

    assertEquals(CapabilityReadiness.Active, status.slot(EngineCapability.Network).readiness)
    assertEquals("https://api.anthropic.com", status.slot(EngineCapability.Network).detail)
    assertFalse(status.needsAttention)
  }

  @Test
  fun keylessProviderNeedsNoKey() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(
        provider = RemoteProvider.Ollama,
        apiKey = "",
        model = "llama3.2:latest",
      ),
      availability = Available,
    )

    assertEquals(CapabilityReadiness.Active, status.slot(EngineCapability.Network).readiness)
    assertFalse(status.needsAttention)
  }

  @Test
  fun unavailableRuntimeMakesSupportedSlotsIncomplete() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(),
      availability = Unavailable("runtime not implemented"),
    )

    assertEquals(
      CapabilityReadiness.Incomplete,
      status.slot(EngineCapability.Network).readiness,
    )
    assertEquals(
      CapabilityReadiness.Incomplete,
      status.slot(EngineCapability.Llm).readiness,
    )
    assertTrue(status.needsAttention)
  }

  @Test
  fun instanceRefWithoutAnInstanceFallsBackToRemote() {
    // The selected instance can be deleted while its ref is still selected; the
    // status must read as an unconfigured Remote, not crash on the missing row.
    val status = engineStatus(EngineRef.Instance("gone"), instance = null, availability = Available)

    assertEquals(EngineKind.Remote, status.kind)
    assertEquals(CapabilityReadiness.Incomplete, status.slot(EngineCapability.Network).readiness)
    assertEquals(CapabilityReadiness.Incomplete, status.slot(EngineCapability.Llm).readiness)
    assertTrue(status.needsAttention)
  }

  @Test
  fun toolsStaysUnusedForEveryRefKind() {
    EngineKind.entries.forEach { kind ->
      val builtin = engineStatus(
        ref = EngineRef.Builtin(kind),
        instance = remoteInstance(),
        availability = Available,
      )
      val instance = engineStatus(
        ref = EngineRef.Instance("conn-1"),
        instance = remoteInstance().copy(kind = kind),
        availability = Available,
      )

      assertEquals(
        CapabilityReadiness.Unused,
        builtin.slot(EngineCapability.Tools).readiness,
        "builtin $kind"
      )
      assertEquals(
        CapabilityReadiness.Unused,
        instance.slot(EngineCapability.Tools).readiness,
        "instance $kind"
      )
    }
  }

  @Test
  fun anInstanceIsNamedByItsOwnLabel() {
    // The chrome words the engine by what the user called it, not by its kind:
    // a connection called "Work Claude" is not a "Remote" in the status sheet.
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance().copy(label = "Work Claude"),
      availability = Available,
    )

    assertEquals("Work Claude", status.label)
  }

  @Test
  fun aBuiltinIsNamedByItsCatalogEntry() {
    // The Free tier's built-in Remote is the official cloud engine, so it is
    // named the way the picker's card names it rather than by its kind.
    val status = engineStatus(
      ref = EngineRef.Builtin(EngineKind.Remote),
      availability = Available,
    )

    assertEquals("Alpha Interplanetary", status.label)
    assertEquals("The official Alpha Interplanetary cloud engine.", status.description)
  }

  @Test
  fun anInstanceWithNoRowFallsBackToTheKindsOwnWords() {
    // The selected instance can be deleted while its ref is still selected, so
    // the status still has to say something.
    val status = engineStatus(EngineRef.Instance("gone"), instance = null, availability = Available)

    assertEquals(EngineKind.Remote.label, status.label)
    assertEquals(EngineKind.Remote.description, status.description)
  }
}
