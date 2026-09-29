package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.tasks.GenerationTask
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskStatus

/**
 * Which planning interaction came up short, so each surface can offer the one
 * action that is missing rather than a generic error.
 */
enum class GenerationProblemKind(
  val headline: String,
) {
  /** No title landed for a project created without one. */
  Title("Couldn't suggest a title"),
  /** No questions landed for the phase's newest round. */
  Questions("Couldn't create questions"),
}


/**
 * The placeholder for a project whose title never landed, shared by the list card
 * and the detail screen's title bar so the same project reads the same way in
 * both. It is a placeholder rather than an error copy on purpose: the reason
 * lives in the failure chip and the engine status sheet, and the fix lives on the
 * project itself.
 */
const val UntitledProjectLabel = "Untitled"

/**
 * The project's current generation problem, or null when there is nothing to act
 * on — the one derivation behind the base UI's recovery affordances.
 *
 * This is the user-driven replacement for an automatic engine fallback: when the
 * selected engine errors, or answers with an empty batch while claiming more is
 * available, the app surfaces the failure and lets the user act, rather than
 * quietly producing content from a different engine than the one the header says
 * is selected. What the *engine* said about it is not re-derived here — the
 * Status sheet's last-activity row already carries that verbatim, from the
 * activity log, so there is one copy of it rather than two.
 *
 * It answers for a project the user is looking at, which is what the Project
 * List's chip needs: a project they have not opened has no affordances to offer,
 * and the failure itself is announced from the nav root against the sheet that
 * explains it (see `AppChromeState.announceFailures`). Nothing here is about
 * whether the user has been told — an announcement is consumed once per activity,
 * and this is the standing fact that a retry is still owed.
 *
 * [tasks] may be the whole task list or one project's slice; only this project's
 * rows are read, so a caller with an app-wide list gets the same answer. Three
 * things are deliberately *not* problems: work still in flight (a spinner already
 * says "being handled"), a title the project has since got, and a phase whose
 * newest round came back with questions — a failure is only worth offering a
 * retry for when the user has nothing to show for the attempt.
 */
fun generationProblemKind(project: Project, tasks: List<GenerationTask>): GenerationProblemKind? {
  val projectTasks = tasks.filter { it.projectId == project.id }
  // A retry is already running, or a first attempt is still going: an error
  // marker over a live spinner would report a failure that is about to resolve
  // itself.
  if (projectTasks.any { it.isActive }) return null

  val failed = projectTasks.filter { it.status == TaskStatus.Failed }

  // Only a project still lacking a title has a title problem — a failed
  // recommendation that the user then typed over is history, not a problem.
  if (project.editableTitle.isBlank() &&
    failed.any { it.kind == TaskKind.TitleRecommendation }
  ) {
    return GenerationProblemKind.Title
  }

  if (project.currentPhaseLatestRound?.hasQuestions(project) == true) return null
  if (project.currentPhaseFailure != null) return GenerationProblemKind.Questions
  // The round write is best-effort, so a task failure with nothing on the phase's
  // newest round is still a question problem even if the outcome never latched.
  if (failed.any { it.kind == TaskKind.QuestionGeneration }) return GenerationProblemKind.Questions

  return null
}
