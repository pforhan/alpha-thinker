import '../thinker_api.dart';
import 'project_service.dart';

class PigeonProjectService implements ProjectService {
  final ThinkerApi _api = ThinkerApi();

  @override
  Future<ProjectDto> createProject(String synopsis) => _api.createProject(synopsis);

  @override
  Future<List<ProjectDto>> getAllProjects() async {
    final projects = await _api.getAllProjects();
    return projects.cast<ProjectDto>();
  }

  @override
  Future<ProjectDto> getProject(String id) => _api.getProject(id);

  @override
  Future<List<QuestionDto>> getUnansweredQuestions(String projectId) async {
    final questions = await _api.getUnansweredQuestions(projectId);
    return questions.cast<QuestionDto>();
  }

  @override
  Future<void> deleteProject(String id) => _api.deleteProject(id);

  @override
  Future<void> updateAnswer(String projectId, String questionId, String text, bool autoArchive) =>
      _api.updateAnswer(projectId, questionId, text, autoArchive);
  
  @override
  Future<void> archiveQuestion(String projectId, String questionId) => _api.archiveQuestion(projectId, questionId);

  @override
  Future<void> unarchiveQuestion(String projectId, String questionId) => _api.unarchiveQuestion(projectId, questionId);
}
