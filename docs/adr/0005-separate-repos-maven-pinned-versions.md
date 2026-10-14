# ADR-0005: One repository per service, Maven, pinned and scanned versions

**Status:** Proposed · **Date:** 2026-10-08

## Decision
- Each service is an independent Maven project with its own CI, Dockerfile and version. Services share no code, only contracts (OpenAPI, JWT claims).
- Versions outside the Spring Boot BOM are pinned in `pom.xml` properties and were checked for known CVEs on 2026-10-08:

| Dependency | Version | Note |
| --- | --- | --- |
| Spring Boot | 4.1.1 | Manages Spring Security 7.1.1, which fixes CVE-2026-47877, CVE-2026-41707 and CVE-2026-47841 |
| springdoc-openapi | 3.1.1 | Boot 4 line |
| bcprov-jdk18on | 1.86 | 1.84 and older carry CVE-2026-13506 and CVE-2026-5598 |
| bucket4j_jdk17-core | 8.20.0 | |
| Cucumber | 7.34.9 | Stay on 7.x until 8.0 matures |
| ArchUnit | 1.5.0 | |

- Every CI build runs OWASP dependency-check (fails at CVSS ≥ 7) and a Trivy image scan. Dependabot proposes upgrades weekly.
- Spring Cloud is not used yet.
