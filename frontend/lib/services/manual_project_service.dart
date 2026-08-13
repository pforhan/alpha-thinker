import '../models/project_models.dart';
import 'project_service.dart';
import 'thinker_api_manual.dart';

class ManualProjectService implements ProjectService {
  final ThinkerApi _api = ThinkerApi();

  @override
  Future<ProjectDto> createProject(String synopsis, {String? title}) =>
      _api.createProject(synopsis, title);

  @override
  Future<List<ProjectDto>> getAllProjects() async {
    return await _api.getAllProjects();
  }

  @override
  Future<ProjectDto> getProject(String id) => _api.getProject(id);

  @override
  Future<List<QuestionDto>> getUnansweredQuestions(String projectId) async {
    return await _api.getUnansweredQuestions(projectId);
  }

  @override
  Future<void> deleteProject(String id) => _api.deleteProject(id);

  @override
  Future<void> updateAnswer(String projectId, String questionId, String text,
      bool isDraft) =>
      _api.updateAnswer(projectId, questionId, text, isDraft);

  @override
  Future<void> ignoreQuestion(String projectId, String questionId) =>
      _api.ignoreQuestion(projectId, questionId);

  @override
  Future<void> unignoreQuestion(String projectId, String questionId) =>
      _api.unignoreQuestion(projectId, questionId);

  @override
  Future<void> deleteAnswer(String projectId, String questionId,
      int answerId) =>
      _api.deleteAnswer(projectId, questionId, answerId);

  @override
  Future<ProjectDto> updateProject(String id, String title, String synopsis,
      ProjectUpdateMode updateMode) =>
      _api.updateProject(id, title, synopsis, updateMode);
}
