package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.repository.EngineRepository
import alphainterplanetary.thinker.testutil.FakeModelDownloader
import alphainterplanetary.thinker.testutil.FakePlatformContext
import alphainterplanetary.thinker.testutil.FakeStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The bridge between the curated catalog and storage: a download writes one
 * file, an install records the row that names it, and using/deleting are
 * separate decisions from having downloaded.
 */
class LocalModelStoreTest {

  private val model: LocalModel = LocalModelCatalog.models.first()

  private fun TestScope.fixture(
    platform: FakePlatformContext = FakePlatformContext(),
    downloader: FakeModelDownloader = FakeModelDownloader(),
    downloadsSupported: Boolean = true,
    storage: FakeStorage = FakeStorage(),
  ): Fixture {
    val repository = EngineRepository(storage, backgroundScope)
    testScheduler.runCurrent()
    return Fixture(
      store = LocalModelStore(
        repository = repository,
        downloader = downloader,
        platformContext = platform,
        downloadsSupported = downloadsSupported,
      ),
      repository = repository,
      platform = platform,
      downloader = downloader,
    )
  }

  private class Fixture(
    val store: LocalModelStore,
    val repository: EngineRepository,
    val platform: FakePlatformContext,
    val downloader: FakeModelDownloader,
  )

  private fun Fixture.markDownloaded() {
    platform.files += checkNotNull(store.localPath(model))
  }

  @Test
  fun `installing records a deterministic LocalFile instance naming the file`() = runTest {
    val fixture = fixture()
    fixture.markDownloaded()

    fixture.store.install(model)

    val instance = assertNotNull(fixture.store.instance(model))
    assertEquals("local-${model.id}", instance.id)
    assertEquals(LocalModelStore.instanceId(model), instance.id)
    assertEquals(EngineKind.LocalFile, instance.kind)
    assertNull(instance.provider)
    assertEquals(model.label, instance.label)
    assertEquals(model.fileName, instance.model)
    assertEquals(model.contextTokens, instance.contextTokens)
    assertEquals("/models/${model.fileName}", instance.localPath)
    assertTrue(fixture.store.isInstalled(model))
  }

  @Test
  fun `installing without the file on disk records nothing`() = runTest {
    val fixture = fixture()

    fixture.store.install(model)

    assertNull(fixture.store.instance(model))
    assertTrue(fixture.repository.instances.value.none { it.kind == EngineKind.LocalFile })
  }

  @Test
  fun `a second install updates the row instead of adding another`() = runTest {
    val fixture = fixture()
    fixture.markDownloaded()

    fixture.store.install(model)
    fixture.store.install(model)

    assertEquals(
      1,
      fixture.repository.instances.value.count { it.kind == EngineKind.LocalFile },
    )
  }

  @Test
  fun `an instance is found by its file name even under another id`() = runTest {
    val fixture = fixture()
    fixture.repository.addOrUpdate(
      EngineInstance(
        id = "legacy-row",
        kind = EngineKind.LocalFile,
        provider = null,
        label = "Legacy",
        model = model.fileName,
        localPath = "/models/${model.fileName}",
      ),
    )

    assertEquals("legacy-row", fixture.store.instance(model)?.id)
  }

  @Test
  fun `download asks for the curated url checksum and size`() = runTest {
    val fixture = fixture()

    assertNotNull(fixture.store.download(model))

    val spec = fixture.downloader.specs.single()
    assertEquals(model.id, spec.modelId)
    assertEquals(model.downloadUrl, spec.url)
    assertEquals("/models/${model.fileName}", spec.destination)
    assertEquals(model.sha256, spec.expectedSha256)
    assertEquals(model.expectedBytes, spec.expectedBytes)
  }

  @Test
  fun `download is skipped when the platform cannot download`() = runTest {
    val fixture = fixture(downloadsSupported = false)

    assertNull(fixture.store.download(model))
    assertTrue(fixture.downloader.specs.isEmpty())
  }

  @Test
  fun `download is skipped without a model directory`() = runTest {
    val fixture = fixture(platform = FakePlatformContext(directory = null))

    assertNull(fixture.store.download(model))
    assertTrue(fixture.downloader.specs.isEmpty())
  }

  @Test
  fun `download is skipped when the model is already installed`() = runTest {
    val fixture = fixture()
    fixture.markDownloaded()
    fixture.store.install(model)

    assertNull(fixture.store.download(model))
    assertTrue(fixture.downloader.specs.isEmpty())
  }

  @Test
  fun `using an installed model selects its instance`() = runTest {
    val fixture = fixture()
    fixture.markDownloaded()
    fixture.store.install(model)

    fixture.store.use(model)
    testScheduler.runCurrent()

    assertEquals(
      EngineRef.Instance(LocalModelStore.instanceId(model)),
      fixture.repository.selected.value,
    )
  }

  @Test
  fun `using a model whose file disappeared does not change the engine`() = runTest {
    val fixture = fixture()
    fixture.markDownloaded()
    fixture.store.install(model)
    fixture.platform.files.clear()

    fixture.store.use(model)
    testScheduler.runCurrent()

    assertEquals(EngineRef.Builtin(EngineKind.Default), fixture.repository.selected.value)
  }

  @Test
  fun `deleting removes the instance and the file`() = runTest {
    val fixture = fixture()
    fixture.markDownloaded()
    fixture.store.install(model)
    fixture.store.use(model)
    testScheduler.runCurrent()

    fixture.store.delete(model)
    testScheduler.runCurrent()

    assertNull(fixture.store.instance(model))
    assertTrue("/models/${model.fileName}" !in fixture.platform.files)
    assertEquals(EngineRef.Builtin(EngineKind.Default), fixture.repository.selected.value)
  }
}
