# Security Policy

## Supported Versions

The DMN Compiler Toolkit provides security patches and updates for the following versions:

| Version | Supported | Notes |
| --- | --- | --- |
| 1.0.x | :white_check_mark: | Current active release line |
| < 1.0.0 / *-SNAPSHOT | :x: | Development builds; not supported for production security patches |

## Reporting a Vulnerability

We take the security and integrity of the DMN Compiler Toolkit seriously. If you discover a security vulnerability, please report it responsibly:

1. **Private Disclosure**: Do **NOT** open a public issue or discussion on GitHub for security vulnerabilities.
2. **GitHub Security Advisory**: Use the [GitHub Private Vulnerability Reporting](https://github.com/finmsg-io/dmn-compiler-toolkit/security/advisories/new) feature on the repository.
3. **Email Disclosure**: Alternatively, email details to security@finmsg.io with:
   - A description of the vulnerability and potential impact.
   - Steps to reproduce or a minimal proof-of-concept DMN / FEEL input.
   - Any proposed remediation or patch.

## Response SLA

- **Initial Acknowledgement**: Within 48 hours of report receipt.
- **Triage & Assessment**: Within 5 business days.
- **Remediation & Patch Release**: Coordinated with the reporter before public disclosure.

## Security Model and Trust Boundaries

- **DMN Compilation Boundary**: In version 1.0.0, the compiler is designed for **trusted and controlled model pipelines**. If ingesting untrusted or user-supplied DMN XML / FEEL expressions, host applications should enforce memory limits and execution timeouts.
- **Runtime Execution**: Generated Java classes and the IR interpreter execute without reflection, native code execution, or dynamic bytecode injection from model XML, ensuring predictable process isolation.
- **Supply Chain**: Releases are published with immutable SemVer tags, checksums, and dependency scans (mvn -Psecurity-scan).
