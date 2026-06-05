import 'package:flutter/material.dart';
import '../pigeon.dart';

class ProjectDetailScreen extends StatefulWidget {
  final ProjectDto project;
  const ProjectDetailScreen({super.key, required this.project});

  @override
  State<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends State<ProjectDetailScreen> {
  final ProjectApi _api = ProjectApi();
  List<QuestionDto>? _questions;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadQuestions();
  }

  Future<void> _loadQuestions() async {
    setState(() => _loading = true);
    try {
      final questions = await _api.getUnansweredQuestions(widget.project.id);
      setState(() {
        _questions = questions.cast<QuestionDto>();
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading questions: $e')),
        );
      }
      setState(() => _loading = false);
    }
  }

  Future<void> _answerQuestion(QuestionDto question) async {
    final answerController = TextEditingController();
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(question.text),
        content: TextField(
          controller: answerController,
          decoration: const InputDecoration(
            hintText: 'Enter your answer...',
          ),
          maxLines: 5,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Submit'),
          ),
        ],
      ),
    );

    if (result == true && answerController.text.isNotEmpty) {
      try {
        await _api.updateAnswer(widget.project.id, question.id, answerController.text, true);
        await _loadQuestions();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error updating answer: $e')),
          );
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.project.editableTitle),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Synopsis:', style: Theme.of(context).textTheme.titleSmall),
                Text(widget.project.synopsis),
              ],
            ),
          ),
          const Divider(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Text('Questions:', style: Theme.of(context).textTheme.titleMedium),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : (_questions == null || _questions!.isEmpty)
                    ? const Center(child: Text('No unanswered questions.'))
                    : ListView.builder(
                        itemCount: _questions!.length,
                        itemBuilder: (context, index) {
                          final question = _questions![index];
                          return Card(
                            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                            child: ListTile(
                              title: Text(question.text),
                              onTap: () => _answerQuestion(question),
                            ),
                          );
                        },
                      ),
          ),
        ],
      ),
    );
  }
}
