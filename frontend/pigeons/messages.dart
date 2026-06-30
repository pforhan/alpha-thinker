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
  int? ignoredAt;
  List<AnswerDto> answers;

  QuestionDto({
    required this.id,
    required this.text,
    required this.timestamp,
    required this.contextId,
     this.ignoredAt,
    required this.answers,
  });
}

class AnswerDto {
  int id;
  String questionId;
  String text;
  int? answeredAt;
  int? modifiedAt;
  int? deletedAt;

  AnswerDto({
    required this.id,
    required this.questionId,
    required this.text,
    this.answeredAt,
    this.modifiedAt,
    this.deletedAt,
  });
}

enum ProjectUpdateMode {
  keep,
  clear,
  revalidate,
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
  void updateAnswer(String projectId, String questionId, String text, bool autoArchive, bool isDraft);
  @async
   void ignoreQuestion(String projectId, String questionId);
  @async
   void unignoreQuestion(String projectId, String questionId);
  @async
   void deleteAnswer(String projectId, String questionId, int answerId);
    @async
    ProjectDto updateProject(String id, String synopsis, ProjectUpdateMode updateMode);
  }

