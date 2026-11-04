package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.testutil.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvalRowStoreTest {
  private val platform = FakePlatformContext()
  private val store = EvalRowStore(platform)

  private fun row(
    modelId: String = "warped-community/Llama-3.2-3B-litert-lm",
    runId: String = "app-20260101000000-abcdef",
    inProgress: Boolean = true,
  ): EvalRow = EvalRow(
    modelId = modelId,
    model = EvalModelMeta(displayName = "Llama 3.2 3B"),
    run = EvalRunMeta(id = runId, hardware = EvalHardware(os = "Android", key = "android-15")),
    warmup = EvalWarmup(ok = true, wallS = 1.5),
    cells = listOf(
      EvalCell(
        fixture = "simple-product",
        interaction = "title",
        run = 1,
        latencyS = 2.5,
        wallS = 2.5,
        raw = "Water Log",
      ),
    ),
    startedAt = "2026-01-01T00:00:00Z",
    inProgress = inProgress,
    deviceKey = "android-15",
  )

  @Test
  fun `a row is stored under a readable slug and its run id`() {
    assertEquals(
      "warped-community_Llama-3.2-3B-litert-lm-app-20260101000000-abcdef.json",
      store.fileName(row()),
    )
    assertEquals("model", EvalRowStore.slugify(""))
    // Non-identity characters collapse, matching the Python harness's slugify.
    assertEquals("_", EvalRowStore.slugify("///"))
  }

  @Test
  fun `a saved row loads back unchanged`() {
    val original = row()

    assertTrue(store.save(original))

    val loaded = store.loadAll()

    assertEquals(1, loaded.size)
    assertEquals(original, loaded.single())
  }

  @Test
  fun `the row file uses the Python harness's field names`() {
    store.save(row())

    val text = platform.textFiles.values.single()

    assertContains(text, "\"in_progress\": true")
    assertContains(text, "\"latency_s\": 2.5")
    assertContains(text, "\"device_key\": \"android-15\"")
    assertContains(text, "\"model_id\": \"warped-community/Llama-3.2-3B-litert-lm\"")
  }

  @Test
  fun `an unreadable file and a non-json file are skipped`() {
    store.save(row())
    platform.writeTextFile("/evals/broken.json", "{ not json")
    platform.writeTextFile("/evals/notes.txt", "ignore me")

    val loaded = store.loadAll()

    assertEquals(1, loaded.size)
  }

  @Test
  fun `fewer fields than the app writes still decode`() {
    // Rows from the Python harness and from older app versions carry a subset
    // of the fields; a missing optional field must not throw the row away.
    platform.writeTextFile(
      "/evals/legacy.json",
      """{"model_id":"legacy/model","run":{"id":"old-run"},"cells":[]}""",
    )

    val loaded = store.loadAll().single()

    assertEquals("legacy/model", loaded.modelId)
    assertEquals(EvalFixtures.Schema, loaded.schema)
    assertEquals(true, loaded.inProgress)
  }

  @Test
  fun `deleting a row removes its file`() {
    val original = row()
    store.save(original)

    assertTrue(store.delete(original))
    assertTrue(store.loadAll().isEmpty())
  }

  @Test
  fun `a platform without an eval directory stores nothing`() {
    val noDirectory = FakePlatformContext(evalDir = null)
    val store = EvalRowStore(noDirectory)

    assertNull(store.directory())
    assertFalse(store.save(row()))
    assertTrue(store.loadAll().isEmpty())
  }

  @Test
  fun `the device key falls back to the hardware fields`() {
    val legacy = EvalRow(
      modelId = "legacy/model",
      run = EvalRunMeta(
        id = "old-run",
        hardware = EvalHardware(
          platform = "android",
          os = "Android",
          osVersion = "15",
          device = "Pixel 9",
          ramGb = 12.0,
        ),
      ),
    )

    // The same rule the app uses when it writes device_key, so an old row and a
    // new row for one phone still land in one group.
    assertEquals("android|android|15|pixel_9|12.0gb", legacy.deviceKeyOrDerived())
    assertEquals("unknown", EvalRow(modelId = "m", run = EvalRunMeta(id = "r")).deviceKeyOrDerived())
  }
}
