package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineAvailability
import alphainterplanetary.thinker.engine.EngineAvailability.Available
import alphainterplanetary.thinker.engine.EngineAvailability.NotImplemented
import alphainterplanetary.thinker.engine.EngineAvailability.Unavailable
import alphainterplanetary.thinker.engine.EngineCapability
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.engine.RemoteProvider
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
    assertTrue(status.slots.all { it.state == CapabilityState.Unused })
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

    assertTrue(status.slots.all { it.state == CapabilityState.Unused })
    assertFalse(status.needsAttention)
  }

  @Test
  fun completeRemoteInstanceIsActiveWithDetailsFromItsFields() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(baseUrl = "  http://localhost:11434  ", model = "  llama3  "),
      availability = Available,
    )

    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Network).state)
    assertEquals("http://localhost:11434", status.slot(EngineCapability.Network).detail)
    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Llm).state)
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

    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Llm).state)
    assertTrue(status.needsAttention)
  }

  @Test
  fun remoteWithBlankFieldsIsIncompleteInBothSlots() {
    val status = engineStatus(
      ref = EngineRef.Instance("conn-1"),
      instance = remoteInstance(baseUrl = "   ", model = "\n"),
      availability = Available,
    )

    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Llm).state)
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

    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
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

    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Network).state)
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

    assertEquals(CapabilityState.Active, status.slot(EngineCapability.Network).state)
    assertFalse(status.needsAttention)
  }

  @Test
  fun unavailableRuntimeMakesSupportedSlotsIncomplete() {
    listOf<EngineAvailability>(
      Unavailable("runtime not implemented"),
      NotImplemented,
    ).forEach { availability ->
      val status = engineStatus(
        ref = EngineRef.Instance("conn-1"),
        instance = remoteInstance(),
        availability = availability,
      )

      assertEquals(
        CapabilityState.Incomplete,
        status.slot(EngineCapability.Network).state,
        "$availability"
      )
      assertEquals(
        CapabilityState.Incomplete,
        status.slot(EngineCapability.Llm).state,
        "$availability"
      )
      assertTrue(status.needsAttention, "$availability")
    }
  }

  @Test
  fun onDeviceAndLocalFileInstancesAreLlmOnly() {
    listOf(EngineKind.OnDevice, EngineKind.LocalFile).forEach { kind ->
      val instance = EngineInstance(
        id = "conn-1",
        kind = kind,
        provider = null,
        label = "Test instance",
      )
      val status = engineStatus(
        ref = EngineRef.Instance("conn-1"),
        instance = instance,
        availability = Available,
      )

      assertEquals(CapabilityState.Unused, status.slot(EngineCapability.Network).state, "$kind")
      assertEquals(CapabilityState.Active, status.slot(EngineCapability.Llm).state, "$kind")
      assertEquals(kind.label, status.slot(EngineCapability.Llm).detail, "$kind")
    }
  }

  @Test
  fun instanceRefWithoutAnInstanceFallsBackToRemote() {
    // The selected instance can be deleted while its ref is still selected; the
    // status must read as an unconfigured Remote, not crash on the missing row.
    val status = engineStatus(EngineRef.Instance("gone"), instance = null, availability = Available)

    assertEquals(EngineKind.Remote, status.kind)
    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Network).state)
    assertEquals(CapabilityState.Incomplete, status.slot(EngineCapability.Llm).state)
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
        CapabilityState.Unused,
        builtin.slot(EngineCapability.Tools).state,
        "builtin $kind"
      )
      assertEquals(
        CapabilityState.Unused,
        instance.slot(EngineCapability.Tools).state,
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
