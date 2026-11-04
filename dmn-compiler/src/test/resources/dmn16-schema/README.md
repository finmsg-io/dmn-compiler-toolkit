# Official DMN 1.6 validation schemas

Unmodified test-only schema files downloaded on 2026-11-03 from the normative machine-readable
documents listed on the [OMG DMN 1.6 publication page](https://www.omg.org/spec/DMN/1.6/).
DMN 1.6 reuses the DMNDI 1.5 and DMN 1.2 DI/DC schemas. Relative imports resolve inside this
directory; exporter tests disallow external DTDs and network schema retrieval.

| File | Official source | SHA-256 |
| --- | --- | --- |
| DMN16.xsd | [DMN16](https://www.omg.org/spec/DMN/20240513/DMN16.xsd) | `861483990D3DF4C771C16E8516BC36A5120EE2051DECC1146FCB18E4F0A4A318` |
| DMNDI15.xsd | [DMNDI15](https://www.omg.org/spec/DMN/20230324/DMNDI15.xsd) | `6E78FE9621D7360C96609523AFEF3DA5BA4FEE6A2AD53EC4CD12D126C7795C50` |
| DI.xsd | [DI](https://www.omg.org/spec/DMN/20180521/DI.xsd) | `73A5908426F12021DA85C9CCE74AE70DB5DDAC1EBE9C9D28D8495F34BEA60F07` |
| DC.xsd | [DC](https://www.omg.org/spec/DMN/20180521/DC.xsd) | `9F57B8E99B00163A65DFAF5DE2A3E3EA4BE041A1BB23228044C50693334D59E6` |

XSD validation establishes structural validity for the tested documents. Separate compiler/runtime
parity tests check observable scalar behavior. Neither check certifies general DMN conformance.
