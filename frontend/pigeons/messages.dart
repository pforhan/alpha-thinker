import 'package:pigeon/pigeon.dart';

class ProjectDto {
  String id;
  String synopsis;
  String editableTitle;
  int createdAt;
  int updatedAt;
  String status;
  List<QuestionDto> questions;

  ProjectDto({
    required this.id,
    required this.synopsis,
    required this.editableTitle,
    required this.createdAt,
    required this.updatedAt,
    required this.status,
    required this.questions,
  });
}


class QuestionDto {
  String id;
  String text;
  int timestamp;
  String contextId;
  int? archivedAt;
  List<AnswerDto> answers;

  QuestionDto({
    required this.id,
    required this.text,
    required this.timestamp,
    required this.contextId,
    this.archivedAt,
    required this.answers,
  });
}

class AnswerDto {
  String questionId;
  String text;
  int answeredAt;
  int? modifiedAt;

  AnswerDto({
    required this.questionId,
    required this.text,
    required this.answeredAt,
    this.modifiedAt,
  });
}

@HostApi()
abstract class ThinkerApi {
  @async
  ProjectDto createProject(String synopsis);
  @async
  List<ProjectDto> getAllProjects();
  @async
  ProjectDto getProject(String id);
  @async
  List<QuestionDto> getUnansweredQuestions(String projectId);
  @async
  void deleteProject(String id);
  @async
  void updateAnswer(String projectId, String questionId, String text, bool autoArchive);
  @async
  void archiveQuestion(String projectId, String questionId);
  @async
  void unarchiveQuestion(String projectId, String questionId);
}
