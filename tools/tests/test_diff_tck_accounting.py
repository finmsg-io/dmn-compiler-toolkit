import unittest
from tools.diff_tck_accounting import diff_accounting, format_diff_report

class TestDiffTckAccounting(unittest.TestCase):
    def test_diff_accounting_no_diff(self):
        data = {
            'statusCounts': {'PASSED': 2},
            'outcomes': [
                {'entryId': 'dmn-tck/0001-input-data', 'caseId': '001', 'backend': 'interpreter', 'status': 'PASSED'},
                {'entryId': 'dmn-tck/0001-input-data', 'caseId': '001', 'backend': 'generated-java', 'status': 'PASSED'}
            ]
        }
        diff = diff_accounting(data, data)
        self.assertEqual(len(diff['regressions']), 0)
        self.assertEqual(len(diff['improvements']), 0)
        self.assertEqual(diff['baseline_passed'], 2)
        self.assertEqual(diff['current_passed'], 2)
        report = format_diff_report(diff)
        self.assertIn('No Regressions Detected', report)

    def test_diff_accounting_regression_and_improvement(self):
        baseline = {
            'statusCounts': {'PASSED': 1},
            'outcomes': [
                {'entryId': 'dmn-tck/0001-input-data', 'caseId': '001', 'backend': 'interpreter', 'status': 'PASSED'},
                {'entryId': 'dmn-tck/0002-string', 'caseId': '001', 'backend': 'interpreter', 'status': 'FAILED', 'diagnostic': 'err1'}
            ]
        }
        current = {
            'statusCounts': {'PASSED': 1},
            'outcomes': [
                {'entryId': 'dmn-tck/0001-input-data', 'caseId': '001', 'backend': 'interpreter', 'status': 'FAILED', 'diagnostic': 'regressed'},
                {'entryId': 'dmn-tck/0002-string', 'caseId': '001', 'backend': 'interpreter', 'status': 'PASSED'}
            ]
        }
        diff = diff_accounting(baseline, current)
        self.assertEqual(len(diff['regressions']), 1)
        self.assertEqual(len(diff['improvements']), 1)
        report = format_diff_report(diff)
        self.assertIn('Regressions (1)', report)
        self.assertIn('Improvements (1)', report)
        self.assertIn('0001-input-data', report)
        self.assertIn('0002-string', report)

if __name__ == '__main__':
    unittest.main()
