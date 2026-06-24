import 'package:shared_preferences/shared_preferences.dart';

class PreferenceService {
  static const String _questionOrderKey = 'question_order_';

  Future<void> saveQuestionOrder(String projectId, List<String> questionIds) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('$_questionOrderKey$projectId', questionIds.join(','));
  }

  Future<List<String>> getQuestionOrder(String projectId) async {
    final prefs = await SharedPreferences.getInstance();
    final orderString = prefs.getString('$_questionOrderKey$projectId');
    if (orderString == null || orderString.isEmpty) {
      return [];
    }
    return orderString.split(',');
  }

  Future<void> clearQuestionOrder(String projectId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('$_questionOrderKey$projectId');
  }
}
