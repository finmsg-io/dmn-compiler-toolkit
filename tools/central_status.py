#!/usr/bin/env python3
"""Read Central deployment status without publishing or printing credentials."""
import base64
import hashlib
import re
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    try:
        deployment_id = str(uuid.UUID(os.environ.get("CENTRAL_DEPLOYMENT_ID", "")))
    except ValueError:
        print("CENTRAL_DEPLOYMENT_ID must be a UUID", file=sys.stderr)
        return 2
    username = os.environ.get("SONATYPE_USERNAME", "")
    password = os.environ.get("SONATYPE_PASSWORD", "")
    if not username or not password:
        print("Sonatype credentials are unavailable", file=sys.stderr)
        return 2
    token = base64.b64encode((username + ":" + password).encode()).decode()
    request = urllib.request.Request(
        "https://central.sonatype.com/api/v1/publisher/status?" + urllib.parse.urlencode({"id": deployment_id}),
        headers={"Authorization": "Bearer " + token}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = json.loads(response.read(1024 * 1024))
    except urllib.error.HTTPError as error:
        print("Central status request failed: HTTP " + str(error.code), file=sys.stderr)
        return 1
    except (OSError, ValueError):
        print("Central status response could not be read", file=sys.stderr)
        return 1
    try:
        paths = json.loads(os.environ.get("CENTRAL_BUNDLE_PATHS", "[]"))
        if not isinstance(paths, list) or len(paths) > 10 or any(
                not isinstance(path, str) or not re.fullmatch(r"[A-Za-z0-9_./-]+", path)
                or ".." in path.split("/") or path.startswith("/") for path in paths):
            raise ValueError()
    except ValueError:
        print("CENTRAL_BUNDLE_PATHS must contain at most 10 relative artifact paths", file=sys.stderr)
        return 2
    files = []
    # Do not forward authorization through a redirect to another service.
    class NoRedirects(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, request, file, code, message, headers, url):
            return None
    opener = urllib.request.build_opener(NoRedirects())
    for path in paths:
        probe = urllib.request.Request(
            "https://central.sonatype.com/api/v1/publisher/deployment/" + deployment_id
            + "/download/" + urllib.parse.quote(path, safe="/"),
            headers={"Authorization": "Bearer " + token}, method="GET")
        entry = {"path": path}
        try:
            with opener.open(probe, timeout=30) as response:
                content = response.read(8 * 1024 * 1024)
                entry.update(status=response.status, bytes=len(content), sha256=hashlib.sha256(content).hexdigest())
        except urllib.error.HTTPError as error:
            entry["status"] = error.code
        except OSError:
            entry["status"] = "unavailable"
        files.append(entry)
    result = {"deploymentId": deployment_id, "deploymentState": data.get("deploymentState"),
              "errors": data.get("errors", {}), "bundleFiles": files}
    output = json.dumps(result, indent=2)
    for value in [token, username, password]:
        output = output.replace(value, "<redacted>")
    print(output)
    return 1 if data.get("deploymentState") == "FAILED" else 0


if __name__ == "__main__":
    sys.exit(main())
