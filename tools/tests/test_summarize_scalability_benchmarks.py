import unittest
import tempfile
import json
from pathlib import Path
from tools.summarize_scalability_benchmarks import (
    load_benchmark_records,
    compute_scalability_metrics,
    generate_markdown,
)


class TestSummarizeScalabilityBenchmarks(unittest.TestCase):
    def test_compute_scalability_metrics(self):
        records = [
            {
                "benchmark": "io.finmsg.dmn.benchmark.CreditApprovalBenchmark.generatedDirect_CreditApproval",
                "method": "generatedDirect_CreditApproval",
                "scenario": "default",
                "threads": 1,
                "mode": "thrpt",
                "score": 1000.0,
                "score_error": 10.0,
                "unit": "ops/s",
                "alloc_bytes_per_op": "500.00",
                "gc_count": "0.00",
                "gc_time_ms": "0.00",
            },
            {
                "benchmark": "io.finmsg.dmn.benchmark.CreditApprovalBenchmark.generatedDirect_CreditApproval",
                "method": "generatedDirect_CreditApproval",
                "scenario": "default",
                "threads": 2,
                "mode": "thrpt",
                "score": 1950.0,
                "score_error": 20.0,
                "unit": "ops/s",
                "alloc_bytes_per_op": "500.00",
                "gc_count": "0.00",
                "gc_time_ms": "0.00",
            },
            {
                "benchmark": "io.finmsg.dmn.benchmark.CreditApprovalBenchmark.generatedDirect_CreditApproval",
                "method": "generatedDirect_CreditApproval",
                "scenario": "default",
                "threads": 4,
                "mode": "thrpt",
                "score": 3800.0,
                "score_error": 30.0,
                "unit": "ops/s",
                "alloc_bytes_per_op": "500.00",
                "gc_count": "0.00",
                "gc_time_ms": "0.00",
            },
        ]
        results = compute_scalability_metrics(records)
        self.assertEqual(len(results), 3)
        self.assertEqual(results[0]["speedup_vs_1t"], "1.00x")
        self.assertEqual(results[0]["parallel_efficiency"], "100.0%")
        self.assertEqual(results[1]["speedup_vs_1t"], "1.95x")
        self.assertEqual(results[1]["parallel_efficiency"], "97.5%")
        self.assertEqual(results[2]["speedup_vs_1t"], "3.80x")
        self.assertEqual(results[2]["parallel_efficiency"], "95.0%")

    def test_load_and_generate_markdown(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            json_file = Path(tmpdir) / "test-1.json"
            sample_json = [
                {
                    "benchmark": "io.finmsg.dmn.benchmark.ScalarArithmeticBenchmark.generatedDirect_ScalarArithmetic",
                    "mode": "thrpt",
                    "threads": 1,
                    "params": {"scenario": "default"},
                    "primaryMetric": {
                        "score": 5000000.0,
                        "scoreError": 10000.0,
                        "scoreUnit": "ops/s",
                    },
                    "secondaryMetrics": {
                        "gc.alloc.rate.norm": {"score": 0.0},
                        "gc.count": {"score": 0.0},
                        "gc.time": {"score": 0.0},
                    },
                }
            ]
            json_file.write_text(json.dumps(sample_json), encoding="utf-8")
            records = load_benchmark_records([json_file])
            self.assertEqual(len(records), 1)
            metrics = compute_scalability_metrics(records)
            md = generate_markdown(metrics, "os=windows\njava=25")
            self.assertIn("DMN Multi-Threaded Scalability Benchmark Summary", md)
            self.assertIn("ScalarArithmetic", md)
            self.assertIn("Generated Java", md)
            self.assertIn("Metric & Column Legend", md)
            self.assertIn("os=windows", md)


if __name__ == "__main__":
    unittest.main()
