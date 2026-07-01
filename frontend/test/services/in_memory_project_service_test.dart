import 'package:flutter_test/flutter_test.dart';
import 'package:frontend/services/in_memory_project_service.dart';

void main() {
  group('generateTitleFromSynopsis', () {
    test('returns empty string for empty synopsis', () {
      expect(generateTitleFromSynopsis(''), '');
      expect(generateTitleFromSynopsis('   '), '');
    });

    test('returns full synopsis when under 30 chars', () {
      expect(generateTitleFromSynopsis('Short synopsis'), 'Short synopsis');
      expect(generateTitleFromSynopsis('Exactly 30 characters long!'), 'Exactly 30 characters long!');
    });

    test('cuts at sentence end (period) before 30 chars', () {
      expect(generateTitleFromSynopsis('Short. This is longer than 30 chars'), 'Short');
      expect(generateTitleFromSynopsis('Ends with period. More text here'), 'Ends with period');
    });

    test('cuts at newline before 30 chars', () {
      expect(generateTitleFromSynopsis('Line one\nLine two continues'), 'Line one');
      expect(generateTitleFromSynopsis('First line\nSecond line'), 'First line');
    });

    test('cuts at 30 chars when no sentence end or newline', () {
      expect(
        generateTitleFromSynopsis('This is a very long synopsis without breaks'),
        'This is a very long synopsis w',
      );
    });

    test('sentence end takes priority over 30 chars', () {
      final synopsis = 'A. This is way longer than thirty characters';
      expect(generateTitleFromSynopsis(synopsis), 'A');
    });

    test('newline takes priority over 30 chars', () {
      final synopsis = 'Short\nThis is way longer than thirty characters';
      expect(generateTitleFromSynopsis(synopsis), 'Short');
    });

    test('sentence end takes priority over newline', () {
      final synopsis = 'Ends with period.\nNew line here';
      expect(generateTitleFromSynopsis(synopsis), 'Ends with period');
    });

    test('trims whitespace from result', () {
      expect(generateTitleFromSynopsis('  Hello world  '), 'Hello world');
      expect(generateTitleFromSynopsis('Short.\n  '), 'Short');
    });

    test('handles multiple sentences, cuts at first', () {
      expect(
        generateTitleFromSynopsis('First. Second. Third.'),
        'First',
      );
    });

    test('handles multiple newlines, cuts at first', () {
      expect(
        generateTitleFromSynopsis('Line one\nLine two\nLine three'),
        'Line one',
      );
    });
  });
}