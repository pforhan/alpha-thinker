import '../thinker_api.dart';
import 'project_service.dart';

class InMemoryProjectService implements ProjectService {
  final List<ProjectDto> _projects = [];
  final Map<String, List<QuestionDto>> _questions = {};

  @override
  Future<ProjectDto> createProject(String synopsis) async {
    final id = DateTime.now().millisecondsSinceEpoch.toString();
    final now = DateTime.now().millisecondsSinceEpoch;
    final project = ProjectDto(
      id: id,
      synopsis: synopsis,
      editableTitle: synopsis.length > 30 ? synopsis.substring(0, 30) + '...' : synopsis,
      createdAt: now,
      updatedAt: now,
      status: 'Draft',
      questions: [],
    );
    _projects.add(project);

    // Mock initial questions
    _questions[id] = [
      QuestionDto(
        id: 'q1',
        text: 'What is the primary problem this project solves?',
        timestamp: now,
        contextId: 'initial',
        answers: [],
      ),
      QuestionDto(
        id: 'q2',
        text: 'Who is the ideal user?',
        timestamp: now,
        contextId: 'initial',
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
    return questions.where((q) => q.answers.isEmpty && q.ignoredAt == null).toList();
  }

  @override
  Future<void> deleteProject(String id) async {
    _projects.removeWhere((p) => p.id == id);
    _questions.remove(id);
  }

  @override
  Future<void> updateAnswer(String projectId, String questionId, String text, bool autoArchive) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    
    final qs = _questions[projectId];
    if (qs == null) return;

    final qIndex = qs.indexWhere((q) => q.id == questionId);
    if (qIndex == -1) return;

    final question = qs[qIndex];
    final answerIndex = question.answers.indexWhere((a) => a.questionId == questionId);
    
    final newAnswer = AnswerDto(
      questionId: questionId,
      text: text,
      answeredAt: now,
    );

    if (answerIndex != -1) {
      question.answers[answerIndex] = newAnswer;
    } else {
      question.answers.add(newAnswer);
    }

    if (autoArchive) {
      qs[qIndex] = QuestionDto(
        id: question.id,
        text: question.text,
        timestamp: question.timestamp,
        contextId: question.contextId,
        ignoredAt: now,
        answers: question.answers,
      );
    }

    final pIndex = _projects.indexWhere((p) => p.id == projectId);
    if (pIndex != -1) {
      _projects[pIndex].updatedAt = now;
    }
  }

  @override
  Future<void> ignoreQuestion(String projectId, String questionId) async {
    final now = DateTime.now().millisecondsSinceEpoch;
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
  Future<void> unignoreQuestion(String projectId, String questionId) async {
    final now = DateTime.now().millisecondsSinceEpoch;
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
}
