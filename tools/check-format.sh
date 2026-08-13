#!/bin/sh
set -eu
mvn --batch-mode -Pformat spotless:check
