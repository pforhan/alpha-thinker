import '../thinker_api.dart';
import 'project_service.dart';

class InMemoryProjectService implements ProjectService {
  final List<ProjectDto> _projects = [];
  final Map<String, List<QuestionDto>> _questions = {};
  final Map<String, List<AnswerDto>> _answers = {};

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
    );
    _projects.add(project);

    // Mock initial questions
    _questions[id] = [
      QuestionDto(
        id: 'q1',
        text: 'What is the primary problem this project solves?',
        timestamp: now,
        contextId: 'initial',
      ),
      QuestionDto(
        id: 'q2',
        text: 'Who is the ideal user?',
        timestamp: now,
        contextId: 'initial',
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
    return _projects.firstWhere((p) => p.id == id);
  }

  @override
  Future<List<QuestionDto>> getUnansweredQuestions(String projectId) async {
    final questions = _questions[projectId] ?? [];
    final answeredIds = (_answers[projectId] ?? []).map((a) => a.questionId).toSet();
    return questions.where((q) => !answeredIds.contains(q.id) && q.archivedAt == null).toList();
  }

  @override
  Future<void> deleteProject(String id) async {
    _projects.removeWhere((p) => p.id == id);
    _questions.remove(id);
    _answers.remove(id);
  }

  @override
  Future<void> updateAnswer(String projectId, String questionId, String text, bool autoArchive) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final answer = AnswerDto(
      questionId: questionId,
      text: text,
      answeredAt: now,
    );

    if (!_answers.containsKey(projectId)) {
      _answers[projectId] = [];
    }
    _answers[projectId]!.add(answer);

    if (autoArchive) {
      final qs = _questions[projectId];
      if (qs != null) {
        final index = qs.indexWhere((q) => q.id == questionId);
        if (index != -1) {
          qs[index] = QuestionDto(
            id: qs[index].id,
            text: qs[index].text,
            timestamp: qs[index].timestamp,
            contextId: qs[index].contextId,
            archivedAt: now,
          );
        }
      }
    }
    
    final pIndex = _projects.indexWhere((p) => p.id == projectId);
    if (pIndex != -1) {
      _projects[pIndex].updatedAt = now;
    }
  }
}
