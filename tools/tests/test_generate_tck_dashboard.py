import unittest
import json
from tools.generate_tck_dashboard import build_dashboard_html

class TestGenerateTckDashboard(unittest.TestCase):
    def test_build_dashboard_html(self):
        sample_data = {
            'revision': '2026-09-04',
            'inventory': {'decisionTableEntries': 1, 'boxedExpressionEntries': 1},
            'statusCounts': {'PASSED': 2, 'FAILED': 0, 'SKIPPED': 0},
            'outcomes': [
                {
                    'entryId': 'dmn-tck/compliance-level-2/0001-input-data',
                    'caseId': '001',
                    'backend': 'interpreter',
                    'status': 'PASSED',
                    'diagnostic': ''
                },
                {
                    'entryId': 'dmn-tck/compliance-level-2/0001-input-data',
                    'caseId': '001',
                    'backend': 'generated-java',
                    'status': 'PASSED',
                    'diagnostic': ''
                }
            ]
        }
        html = build_dashboard_html(sample_data)
        self.assertIn('DMN 1.5 TCK Conformance Dashboard', html)
        self.assertIn('compliance-level-2/0001-input-data', html)
        self.assertIn('100.00%', html)

if __name__ == '__main__':
    unittest.main()
