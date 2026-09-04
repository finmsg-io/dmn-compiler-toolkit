## Description

- Brief summary of changes and motivation.
- Links to related issues or ADRs.

## Checklist

- [ ] Compiles and passes all unit/integration tests (`mvn verify`)
- [ ] Source formatting enforced (`mvn -Pformat spotless:check`)
- [ ] Documentation updated and verified (`mkdocs build --strict`, `python tools/verify_documentation.py`)
- [ ] No silent skips or reduced TCK conformance denominator
- [ ] No reflection or dynamic bytecode in generated code paths
