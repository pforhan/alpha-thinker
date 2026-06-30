import '../thinker_api.dart';

abstract class ProjectService {
  Future<ProjectDto> createProject(String synopsis);
  Future<List<ProjectDto>> getAllProjects();
  Future<ProjectDto> getProject(String id);
  Future<List<QuestionDto>> getUnansweredQuestions(String projectId);
  Future<void> deleteProject(String id);
  Future<void> updateAnswer(String projectId, String questionId, String text, bool autoArchive, bool isDraft);
  Future<void> ignoreQuestion(String projectId, String questionId);
  Future<void> unignoreQuestion(String projectId, String questionId);
  Future<void> deleteAnswer(String projectId, String questionId, int answerId);
  Future<ProjectDto> updateProject(String id, String synopsis, bool clearAnswers);
}
