import 'package:flutter/material.dart';
import '../pigeon.dart';
import 'project_detail_screen.dart';

class ProjectListScreen extends StatefulWidget {
  const ProjectListScreen({super.key});

  @override
  State<ProjectListScreen> createState() => _ProjectListScreenState();
}

class _ProjectListScreenState extends State<ProjectListScreen> {
  final ProjectApi _api = ProjectApi();
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
      final projects = await _api.getAllProjects();
      setState(() {
        _projects = projects.cast<ProjectDto>();
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading projects: $e')),
        );
      }
      setState(() => _loading = false);
    }
  }

  Future<void> _createProject() async {
    final synopsisController = TextEditingController();
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('New Project'),
        content: TextField(
          controller: synopsisController,
          decoration: const InputDecoration(
            hintText: 'Enter your project idea (synopsis)...',
          ),
          maxLines: 3,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Create'),
          ),
        ],
      ),
    );

    if (result == true && synopsisController.text.isNotEmpty) {
      try {
        final newProject = await _api.createProject(synopsisController.text);
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
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error creating project: $e')),
          );
        }
      }
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
              ? Center(
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
                )
              : ListView.builder(
                  itemCount: _projects!.length,
                  itemBuilder: (context, index) {
                    final project = _projects![index];
                    return ListTile(
                      title: Text(project.editableTitle),
                      subtitle: Text(project.synopsis, maxLines: 2, overflow: TextOverflow.ellipsis),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => ProjectDetailScreen(project: project),
                          ),
                        ).then((_) => _refreshProjects());
                      },
                      onLongPress: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (context) => AlertDialog(
                            title: const Text('Delete Project?'),
                            content: const Text('This action cannot be undone.'),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                              TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete', style: TextStyle(color: Colors.red))),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          await _api.deleteProject(project.id);
                          await _refreshProjects();
                        }
                      },
                    );
                  },
                ),
      floatingActionButton: FloatingActionButton(
        onPressed: _createProject,
        child: const Icon(Icons.add),
      ),
    );
  }
}
