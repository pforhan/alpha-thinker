package alphainterplanetary.thinker.export

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Schema version of the portable project interchange envelope/wrapper.
 *
 * Compatibility: readers should reject or migrate envelopes with an unsupported
 * newer [PROJECT_INTERCHANGE_SCHEMA_VERSION] they cannot safely interpret, and
 * may accept older known versions via migration. Unknown fields must be ignored
 * where possible ([ProjectInterchange.json] uses `ignoreUnknownKeys`) to allow
 * forward-compatible additions.
 */
const val PROJECT_INTERCHANGE_SCHEMA_VERSION: Int = 1

/**
 * Recommended file extension for portable interchange exports.
 * (MIME/type and compression/wrapping are defined by the export pipeline.)
 */
const val PROJECT_INTERCHANGE_EXTENSION: String = "thinker"

/**
 * Portable, versioned interchange envelope for transferring project data between
 * Thinker instances. Based on the canonical [ExportSnapshot] (or a compatible
 * projection) plus interchange metadata.
 *
 * A single envelope can carry one or more projects so that single-project and
 * global/multi-project exports share the same wrapper format.
 *
 * Scope: each [ProjectInterchangeProject] carries project core fields, rounds,
 * phases, questions/answers, drafts, ignored/compacted state, and committed
 * answer revision history via its [ExportSnapshot]. Per-phase summaries and
 * render-only [alphainterplanetary.thinker.model.ExportConfig] options are not
 * embedded by default (summaries are derived, intent/model-specific artifacts);
 * they may be added as optional fields in future schema versions.
 */
@Serializable
data class ProjectInterchangeEnvelope(
  /** Envelope/wrapper schema version (independent of embedded snapshot schema). */
  val schemaVersion: Int = PROJECT_INTERCHANGE_SCHEMA_VERSION,
  /** When this interchange file was produced (epoch milliseconds). */
  val exportedAtEpochMs: Long,
  /** Producing application identifier for provenance. */
  val appId: String = "alpha-thinker",
  /** Producing application version, if known. */
  val appVersion: String? = null,
  /** One or more project payloads (single-project exports contain one entry). */
  val projects: List<ProjectInterchangeProject>,
) {
  init {
    require(projects.isNotEmpty()) { "Interchange envelope must contain at least one project" }
  }
}

/** One project's portable payload within an interchange envelope. */
@Serializable
data class ProjectInterchangeProject(
  /** Canonical snapshot/projection of the project's export-relevant state. */
  val snapshot: ExportSnapshot,
)

object ProjectInterchange {
  /**
   * JSON format for interchange files: defaults encoded for explicitness, unknown
   * keys ignored for forward compatibility, and pretty-printed for portability.
   */
  val json: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  /** Wraps [snapshots] in a versioned interchange envelope. */
  fun envelopeOf(
    snapshots: List<ExportSnapshot>,
    exportedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    appVersion: String? = null,
  ): ProjectInterchangeEnvelope = ProjectInterchangeEnvelope(
    exportedAtEpochMs = exportedAtEpochMs,
    appVersion = appVersion,
    projects = snapshots.map { ProjectInterchangeProject(it) },
  )

  /** Wraps a single [snapshot] in the standard interchange envelope. */
  fun envelopeOf(
    snapshot: ExportSnapshot,
    exportedAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    appVersion: String? = null,
  ): ProjectInterchangeEnvelope = envelopeOf(listOf(snapshot), exportedAtEpochMs, appVersion)

  fun encode(envelope: ProjectInterchangeEnvelope): String = json.encodeToString(envelope)

  fun decode(text: String): ProjectInterchangeEnvelope = json.decodeFromString(text)
}
