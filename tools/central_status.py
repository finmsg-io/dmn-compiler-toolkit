#!/usr/bin/env python3
"""Read Central deployment status without publishing or printing credentials."""
import base64
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
    result = {"deploymentId": deployment_id, "deploymentState": data.get("deploymentState"),
              "errors": data.get("errors", {})}
    output = json.dumps(result, indent=2)
    for value in [token, username, password]:
        output = output.replace(value, "<redacted>")
    print(output)
    return 1 if data.get("deploymentState") == "FAILED" else 0


if __name__ == "__main__":
    sys.exit(main())
