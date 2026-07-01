import '../thinker_api.dart';
import '../thinker_api_extensions.dart';
import 'project_service.dart';
import 'package:flutter/foundation.dart'; // TODO remove after removing debugPrints

class InMemoryProjectService implements ProjectService {
  final List<ProjectDto> _projects = [];
  final Map<String, List<QuestionDto>> _questions = {};

  @override
  Future<ProjectDto> createProject(String synopsis, {String? title}) async {
    final id = DateTime
        .now()
        .millisecondsSinceEpoch
        .toString();
    final now = DateTime
        .now()
        .millisecondsSinceEpoch;
    final project = ProjectDto(
      id: id,
      synopsis: synopsis.trim(),
      editableTitle: title?.trim() ?? (synopsis.trim().length > 30
          ? '${synopsis.trim().substring(0, 30)}...'
          : synopsis.trim()),
      createdAt: now,
      updatedAt: now,
      status: 'Draft',
      questions: [],
    );
    _projects.add(project);

    // Mock initial questions (20 Seed Questions for Lite edition)
    _questions[id] = [
      QuestionDto(
        id: '${id}_q1',
        text: 'What is the primary problem this project solves?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q2',
        text: 'Who is the ideal user or beneficiary?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q3',
        text: 'What is the single most important goal?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q4',
        text: 'What are three key milestones for the first month?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q5',
        text: 'What resources (time, money, tools) are currently available?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q6',
        text: 'What resources are still needed?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q7',
        text: 'What is the target completion date?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q8',
        text: 'What are the top three risks to success?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q9',
        text: 'How will you know if the project is successful?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q10',
        text: 'What is the "Minimum Viable Product" (MVP) version?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q11',
        text: 'What are the key technical constraints or requirements?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q12',
        text: 'Who are the primary stakeholders and decision-makers?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q13',
        text: 'What similar projects or competitors have you looked at?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q14',
        text: 'What is the long-term vision for this project?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q15',
        text: 'What are the non-negotiable features or qualities?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q16',
        text: 'How will this project be maintained or supported later?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q17',
        text: 'What is the estimated total budget?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q18',
        text: 'Are there any legal, ethical, or compliance factors?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q19',
        text: 'How will you promote or distribute the final result?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
      QuestionDto(
        id: '${id}_q20',
        text: 'What is the very first step you need to take?',
        timestamp: now,
        contextId: 'seed',
        answers: [],
      ),
    ];

    return project;
  }

  @override
  Future<List<ProjectDto>> getAllProjects() async {
    return List.unmodifiable(_projects);
  }

  @override
  Future<ProjectDto> getProject(String id) async {
    final project = _projects.firstWhere((p) => p.id == id);
    final qs = _questions[id] ?? [];

    return ProjectDto(
      id: project.id,
      synopsis: project.synopsis,
      editableTitle: project.editableTitle,
      createdAt: project.createdAt,
      updatedAt: project.updatedAt,
      status: project.status,
      questions: List.from(qs),
    );
  }

  @override
  Future<List<QuestionDto>> getUnansweredQuestions(String projectId) async {
    final questions = _questions[projectId] ?? [];
    return questions.where((q) {
      final current = q.currentAnswer;
      final isAnswered = current != null && current.isComplete;
      return !isAnswered && q.ignoredAt == null;
    }).toList();
  }

  @override
  Future<void> deleteProject(String id) async {
    _projects.removeWhere((p) => p.id == id);
    _questions.remove(id);
  }

  @override
  Future<void> updateAnswer(String projectId, String questionId, String text,
      bool isDraft) async {
    final now = DateTime
        .now()
        .millisecondsSinceEpoch;

    final qs = _questions[projectId];
    if (qs == null) return;

    final qIndex = qs.indexWhere((q) => q.id == questionId);
    if (qIndex == -1) return;

    final question = qs[qIndex];

    if (isDraft && question.isAnswered) {
      throw Exception('Cannot add a draft answer to an answered question');
    }

    if (isDraft) {
      question.answers.clear();
    }

    final newAnswer = AnswerDto(
      id: DateTime
          .now()
          .millisecondsSinceEpoch,
      questionId: questionId,
      text: text.trim(),
      answeredAt: isDraft ? null : now,
    );

    question.answers.add(newAnswer);

    final pIndex = _projects.indexWhere((p) => p.id == projectId);
    if (pIndex != -1) {
      _projects[pIndex].updatedAt = now;
    }
  }

  @override
  Future<void> ignoreQuestion(String projectId, String questionId) async {
    final now = DateTime
        .now()
        .millisecondsSinceEpoch;
    final qs = _questions[projectId];
    if (qs == null) return;

    final index = qs.indexWhere((q) => q.id == questionId);
    if (index != -1) {
      final q = qs[index];
      qs[index] = QuestionDto(
        id: q.id,
        text: q.text,
        timestamp: q.timestamp,
        contextId: q.contextId,
        ignoredAt: now,
        answers: q.answers,
      );
    }

    final pIndex = _projects.indexWhere((p) => p.id == projectId);
    if (pIndex != -1) {
      _projects[pIndex].updatedAt = now;
    }
  }

  @override
  Future<void> deleteAnswer(String projectId, String questionId,
      int answerId) async {
    debugPrint(
        'Attempting to delete answer $answerId for question $questionId in project $projectId');
    final qs = _questions[projectId];
    if (qs == null) {
      debugPrint('Delete failed: Project questions not found.');
      return;
    }

    final qIndex = qs.indexWhere((q) => q.id == questionId);
    if (qIndex == -1) {
      debugPrint(
          'Delete failed: Question $questionId not found in project $projectId');
      return;
    }

    final question = qs[qIndex];
    final aIndex = question.answers.indexWhere((a) => a.id == answerId);
    if (aIndex != -1) {
      debugPrint('Successfully located answer at index $aIndex');
      final answer = question.answers[aIndex];
      question.answers[aIndex] = AnswerDto(
        id: answer.id,
        questionId: answer.questionId,
        text: answer.text,
        answeredAt: answer.answeredAt,
        modifiedAt: answer.modifiedAt,
        deletedAt: DateTime
            .now()
            .millisecondsSinceEpoch,
      );
    } else {
      debugPrint(
          'Delete failed: Answer $answerId not found for question $questionId');
    }
  }

  @override
  Future<void> unignoreQuestion(String projectId, String questionId) async {
    final now = DateTime
        .now()
        .millisecondsSinceEpoch;
    final qs = _questions[projectId];
    if (qs == null) return;

    final index = qs.indexWhere((q) => q.id == questionId);
    if (index != -1) {
      final q = qs[index];
      qs[index] = QuestionDto(
        id: q.id,
        text: q.text,
        timestamp: q.timestamp,
        contextId: q.contextId,
        ignoredAt: null,
        answers: q.answers,
      );
    }

    final pIndex = _projects.indexWhere((p) => p.id == projectId);
    if (pIndex != -1) {
      _projects[pIndex].updatedAt = now;
    }
  }

  @override
  Future<ProjectDto> updateProject(String id, String title, String synopsis,
      ProjectUpdateMode updateMode) async {
    final pIndex = _projects.indexWhere((p) => p.id == id);
    if (pIndex == -1) throw Exception('Project not found');

    final project = _projects[pIndex];
    final now = DateTime
        .now()
        .millisecondsSinceEpoch;

    project.synopsis = synopsis.trim();
    project.editableTitle = title.trim();
    project.updatedAt = now;

    if (updateMode == ProjectUpdateMode.clear) {
      final qs = _questions[id];
      if (qs != null) {
          for (var q in qs) {
            q.answers.clear(); // Using clear here as it's a mock, or mark deleted
            q.ignoredAt = null;
          }
      }
    }

    return project;
  }
}
