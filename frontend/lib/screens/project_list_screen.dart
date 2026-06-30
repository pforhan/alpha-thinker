import 'package:flutter/material.dart';
import '../injection.dart';
import '../thinker_api.dart';
import '../services/project_service.dart';
import 'project_detail_screen.dart';
import '../widgets/edit_project_dialog.dart';

class ProjectListScreen extends StatefulWidget {
  const ProjectListScreen({super.key});

  @override
  State<ProjectListScreen> createState() => _ProjectListScreenState();
}

class _ProjectListScreenState extends State<ProjectListScreen> {
  final ProjectService _service = getIt<ProjectService>();
  List<ProjectDto>? _projects;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _refreshProjects();
  }

  Future<void> _refreshProjects() async {
    setState(() => _loading = true);
    try {
      final projects = await _service.getAllProjects();
      setState(() {
        _projects = projects;
        _loading = false;
      });
    } catch (e) {
      debugPrint('Error loading projects: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error loading projects: $e'),
            duration: const Duration(seconds: 10),
            action: SnackBarAction(label: 'Dismiss', onPressed: () {}),
          ),
        );
      }
      setState(() => _loading = false);
    }
  }

  Future<void> _createProject() async {
    await showDialog(
      context: context,
      builder: (context) => EditProjectDialog(
        onSave: (title, synopsis, _) async {
          if (synopsis.isNotEmpty) {
            try {
              final newProject = await _service.createProject(
                synopsis,
                title: title.isNotEmpty ? title : null,
              );
              await _refreshProjects();
              if (mounted) {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => ProjectDetailScreen(project: newProject),
                  ),
                ).then((_) => _refreshProjects());
              }
            } catch (e) {
              debugPrint('Error creating project: $e');
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('Error creating project: $e'),
                    duration: const Duration(seconds: 10),
                    action: SnackBarAction(label: 'Dismiss', onPressed: () {}),
                  ),
                );
              }
            }
          }
        },
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text('No projects yet.'),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _createProject,
            child: const Text('Create your first project'),
          ),
        ],
      ),
    );
  }

  Widget _buildProjectList() {
    return ListView.builder(
      itemCount: _projects!.length,
      itemBuilder: (context, index) {
        final project = _projects![index];
        return ListTile(
          title: Text(project.editableTitle),
          subtitle: Text(
              project.synopsis, maxLines: 2, overflow: TextOverflow.ellipsis),
          trailing: const Icon(Icons.chevron_right),
          onTap: () {
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => ProjectDetailScreen(project: project),
              ),
            ).then((_) => _refreshProjects());
          },
          onLongPress: () => _confirmDeleteProject(project),
        );
      },
    );
  }

  Future<void> _confirmDeleteProject(ProjectDto project) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) =>
          AlertDialog(
            title: const Text('Delete Project?'),
            content: const Text('This action cannot be undone.'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false),
                  child: const Text('Cancel')),
              TextButton(
                  onPressed: () => Navigator.pop(context, true),
                  child: const Text(
                      'Delete', style: TextStyle(color: Colors.red))),
            ],
          ),
    );
    if (confirm == true) {
      await _service.deleteProject(project.id);
      await _refreshProjects();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Alpha Thinker'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _refreshProjects,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : (_projects == null || _projects!.isEmpty)
          ? _buildEmptyState()
          : _buildProjectList(),
      floatingActionButton: FloatingActionButton(
        onPressed: _createProject,
        child: const Icon(Icons.add),
      ),
    );
  }
}
