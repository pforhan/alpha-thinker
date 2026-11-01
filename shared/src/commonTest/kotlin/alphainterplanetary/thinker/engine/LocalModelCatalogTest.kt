package alphainterplanetary.thinker.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The curated Local catalog (MODEL-CATALOG-PLAN.md stage 3): every row must be
 * directly downloadable as one LiteRT file, identifiable by id, file name, and
 * repository, and carry the metadata the download and UI rely on.
 */
class LocalModelCatalogTest {

  private val models = LocalModelCatalog.models

  @Test
  fun `the catalog is not empty`() {
    assertTrue(models.isNotEmpty())
  }

  @Test
  fun `every model is one LiteRT file on Hugging Face`() {
    models.forEach { model ->
      assertTrue(model.id.isNotBlank(), "id")
      assertTrue(model.label.isNotBlank(), "label")
      assertTrue(model.description.isNotBlank(), "description")
      assertTrue(model.license.isNotBlank(), "license")
      assertTrue(model.fileName.endsWith(".litertlm"), model.fileName)
      assertEquals("litertlm", model.fileExtension)
      assertEquals(
        "https://huggingface.co/${model.repoId}/resolve/main/${model.fileName}",
        model.downloadUrl,
      )
    }
  }

  @Test
  fun `every model carries what a resumable verified download needs`() {
    models.forEach { model ->
      assertTrue(model.expectedBytes > 0L, "${model.id} size")
      assertTrue(model.contextTokens > 0, "${model.id} context")
      assertEquals(64, model.sha256.length, "${model.id} checksum length")
      assertTrue(
        model.sha256.all { it in '0'..'9' || it in 'a'..'f' },
        "${model.id} checksum is lowercase hex",
      )
    }
  }

  @Test
  fun `ids file names and repositories are unique`() {
    assertEquals(models.size, models.map { it.id }.distinct().size)
    assertEquals(models.size, models.map { it.fileName }.distinct().size)
    assertEquals(models.size, models.map { it.repoId }.distinct().size)
  }

  @Test
  fun `lookups resolve each curated model`() {
    models.forEach { model ->
      assertEquals(model, LocalModelCatalog.byId(model.id))
      assertEquals(model, LocalModelCatalog.byFileName(model.fileName))
      assertEquals(model, LocalModelCatalog.byRepoId(model.repoId))
    }
  }

  @Test
  fun `an unknown lookup is null`() {
    assertNull(LocalModelCatalog.byId("missing"))
    assertNull(LocalModelCatalog.byFileName("missing.litertlm"))
    assertNull(LocalModelCatalog.byRepoId("missing/repo"))
  }
}
