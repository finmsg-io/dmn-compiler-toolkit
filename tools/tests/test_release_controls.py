from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}


class ReleaseControlsTest(unittest.TestCase):
    def test_ordinary_ci_has_no_publication_authority(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        self.assertNotIn("packages: write", workflow)
        self.assertNotIn("mvn --batch-mode deploy", workflow)
        self.assertNotIn("tags:", workflow)

    def test_release_workflow_separates_verification_and_publication(self) -> None:
        workflow = (ROOT / ".github" / "workflows" / "publish-release.yml").read_text(encoding="utf-8")
        self.assertIn("verify-release:", workflow)
        self.assertIn("publish-release:", workflow)
        self.assertIn("needs: verify-release", workflow)
        self.assertIn("environment: release", workflow)
        self.assertEqual(1, workflow.count("packages: write"))
        self.assertIn("mvn --batch-mode -Pformat,release clean verify", workflow)
        self.assertIn("python tools/verify_release.py --tag", workflow)

    def test_maven_deployment_is_default_deny_and_deploys_at_end(self) -> None:
        model = ET.parse(ROOT / "pom.xml").getroot()
        default_skip = model.findtext("./m:properties/m:maven.deploy.skip", namespaces=NAMESPACE)
        deploy_at_end = model.findtext(
            "./m:build/m:pluginManagement/m:plugins/m:plugin[m:artifactId='maven-deploy-plugin']"
            "/m:configuration/m:deployAtEnd",
            namespaces=NAMESPACE,
        )
        release_skip = model.findtext(
            "./m:profiles/m:profile[m:id='release']/m:properties/m:maven.deploy.skip",
            namespaces=NAMESPACE,
        )
        self.assertEqual("true", default_skip)
        self.assertEqual("true", deploy_at_end)
        self.assertEqual("false", release_skip)


if __name__ == "__main__":
    unittest.main()
