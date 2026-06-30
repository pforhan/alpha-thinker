import 'package:flutter/material.dart';
import '../thinker_api.dart';

class EditProjectDialog extends StatefulWidget {
  final ProjectDto? project;
  final Function(String title, String synopsis, ProjectUpdateMode mode) onSave;

  const EditProjectDialog({
    super.key,
    this.project,
    required this.onSave,
  });

  @override
  State<EditProjectDialog> createState() => _EditProjectDialogState();
}

class _EditProjectDialogState extends State<EditProjectDialog> {
  late TextEditingController _synopsisController;
  late TextEditingController _titleController;
  ProjectUpdateMode _mode = ProjectUpdateMode.keep;
  bool _showTitleField = false;

  @override
  void initState() {
    super.initState();
    _synopsisController = TextEditingController(text: widget.project?.synopsis ?? '');
    _titleController = TextEditingController(text: widget.project?.editableTitle ?? '');
    if (widget.project == null) {
      _showTitleField = false;
    }
  }

  @override
  void dispose() {
    _synopsisController.dispose();
    _titleController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.project != null;
    return AlertDialog(
      title: Text(isEdit ? 'Edit Project' : 'New Project'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (isEdit) ...[
              Text('Title', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              TextField(
                controller: _titleController,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  hintText: 'Project title',
                ),
              ),
              const SizedBox(height: 16),
            ] else ...[
              if (!_showTitleField)
                TextButton(
                  onPressed: () => setState(() => _showTitleField = true),
                  child: const Text('Add title (optional)'),
                )
              else
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Title', style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _titleController,
                      decoration: const InputDecoration(
                        border: OutlineInputBorder(),
                        hintText: 'Project title',
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
            ],
            Text('Synopsis', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            TextField(
              controller: _synopsisController,
              autofocus: true,
              maxLines: null,
              minLines: 3,
              keyboardType: TextInputType.multiline,
              decoration: InputDecoration(
                border: const OutlineInputBorder(),
                hintText: isEdit 
                  ? 'Enter project synopsis...' 
                  : 'Enter your project idea (synopsis)...',
              ),
            ),
            const SizedBox(height: 24),
            if (isEdit) ...[
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
            ],
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
            widget.onSave(_titleController.text.trim(), _synopsisController.text.trim(), _mode);
            Navigator.pop(context);
          },
          child: Text(isEdit ? 'Save' : 'Create'),
        ),
      ],
    );
  }
}
