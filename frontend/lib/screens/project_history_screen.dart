import 'package:flutter/material.dart';
import '../thinker_api.dart';
import '../services/project_service.dart';
import '../injection.dart';

class ProjectHistoryScreen extends StatefulWidget {
  final ProjectDto project;
  const ProjectHistoryScreen({super.key, required this.project});

  @override
  State<ProjectHistoryScreen> createState() => _ProjectHistoryScreenState();
}

class _ProjectHistoryScreenState extends State<ProjectHistoryScreen> {
  final ProjectService _service = getIt<ProjectService>();
  bool _loading = true;
  List<QuestionDto> _allQuestions = [];
  String _filter = 'Answered';

  @override
  void initState() {
    super.initState();
    _loadProjectData();
  }

  Future<void> _loadProjectData() async {
    setState(() => _loading = true);
    try {
      debugPrint('Fetching project history for: ${widget.project.id}');
      final project = await _service.getProject(widget.project.id);
      debugPrint('Project loaded. Questions count: ${project.questions.length}');
      for (var q in project.questions) {
        debugPrint('Question ${q.id}: text="${q.text}", archivedAt=${q.archivedAt}, answersCount=${q.answers.length}');
      }
      
      setState(() {
        _allQuestions = project.questions;
        _loading = false;
      });
    } catch (e) {
      debugPrint('Error loading project history: $e');
      setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Project History'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(50),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ChoiceChip(
                  label: const Text('Answered'),
                  selected: _filter == 'Answered',
                  onSelected: (selected) {
                    setState(() => _filter = 'Answered');
                  },
                ),
                ChoiceChip(
                  label: const Text('Archived'),
                  selected: _filter == 'Archived',
                  onSelected: (selected) {
                    setState(() => _filter = 'Archived');
                  },
                ),
              ],
            ),
          ),
        ),
      ),
      body: _loading 
        ? const Center(child: CircularProgressIndicator())
        : ListView.builder(
            itemCount: _allQuestions.length,
            itemBuilder: (context, index) {
              final question = _allQuestions[index];
              final isArchived = question.archivedAt != null;
              final hasAnswer = question.answers.isNotEmpty;
              
              if (_filter == 'Answered' && (isArchived || !hasAnswer)) return const SizedBox.shrink();
              if (_filter == 'Archived' && !isArchived) return const SizedBox.shrink();

              return Card(
                margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: ListTile(
                  title: Text(question.text),
                  subtitle: Text(isArchived ? 'Archived' : 'Answered'),
                  trailing: IconButton(
                    icon: Icon(isArchived ? Icons.unarchive : Icons.archive),
                    onPressed: () async {
                      if (isArchived) {
                        await _service.unarchiveQuestion(widget.project.id, question.id);
                      } else {
                        await _service.archiveQuestion(widget.project.id, question.id);
                      }
                      _loadProjectData();
                    },
                  ),
                ),
              );
            },
          ),
    );
  }
}
