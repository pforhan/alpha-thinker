import 'package:flutter/material.dart';
import '../thinker_api.dart';

class EditProjectDialog extends StatefulWidget {
  final ProjectDto project;
  final Function(String synopsis, bool clearAnswers) onSave;

  const EditProjectDialog({
    super.key,
    required this.project,
    required this.onSave,
  });

  @override
  State<EditProjectDialog> createState() => _EditProjectDialogState();
}

class _EditProjectDialogState extends State<EditProjectDialog> {
  late TextEditingController _controller;
  bool _clearAnswers = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.project.synopsis);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Edit Synopsis'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _controller,
            maxLines: null,
            decoration: const InputDecoration(
              border: OutlineInputBorder(),
              hintText: 'Enter project synopsis...',
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              const Text('Clear all prior answers?'),
              Checkbox(
                value: _clearAnswers,
                onChanged: (val) {
                  setState(() => _clearAnswers = val ?? false);
                },
              ),
            ],
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () {
            widget.onSave(_controller.text, _clearAnswers);
            Navigator.pop(context);
          },
          child: const Text('Save'),
        ),
      ],
    );
  }
}
