# `dmn-frontend-xml`

Namespace-aware VTD-XML reader and round-trip writer module of the DMN Compiler Toolkit.

## Key Assets

- **`DmnXmlReader`**: High-performance VTD-XML reader converting DMN 1.2–1.6 XML files into canonical `Definitions` Protobuf semantic models.
- **`DmnWriter` & `XmlEmitter`**: Format-preserving XML writer with scoped QName `typeRef` prefix preservation and structured extension handling.

## Usage

```java
Definitions model = new DmnXmlReader().read(path);
byte[] xmlBytes = new DmnWriter().write(model);
```
