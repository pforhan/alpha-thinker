import 'package:flutter/material.dart';
import '../thinker_api.dart';

class EditProjectDialog extends StatefulWidget {
  final ProjectDto project;
  final Function(String synopsis, ProjectUpdateMode mode) onSave;

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
  ProjectUpdateMode _mode = ProjectUpdateMode.keep;

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
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _controller,
              maxLines: null,
              keyboardType: TextInputType.multiline,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                hintText: 'Enter project synopsis...',
                labelText: 'Synopsis',
              ),
            ),
            const SizedBox(height: 24),
            const Text(
              'Handling prior answers:',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            RadioListTile<ProjectUpdateMode>(
              title: const Text('Keep existing answers'),
              value: ProjectUpdateMode.keep,
              groupValue: _mode,
              onChanged: (val) => setState(() => _mode = val!),
            ),
            RadioListTile<ProjectUpdateMode>(
              title: const Text('Clear all answers'),
              value: ProjectUpdateMode.clear,
              groupValue: _mode,
              onChanged: (val) => setState(() => _mode = val!),
            ),
            // TODO when we have LLM integration
            // RadioListTile<ProjectUpdateMode>(
            //   title: const Text('AI Revalidate relevance'),
            //   value: ProjectUpdateMode.revalidate,
            //   groupValue: _mode,
            //   onChanged: (val) => setState(() => _mode = val!),
            // ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () {
            widget.onSave(_controller.text, _mode);
            Navigator.pop(context);
          },
          child: const Text('Save'),
        ),
      ],
    );
  }
}
