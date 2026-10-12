# Java 25 LTS upgrade notes

The inventory service targets Java 25 through the Maven `java.version` property and uses Spring Boot 3.5.13.

## Verified

- Microsoft OpenJDK 25.0.2 LTS and Maven 3.10.0.
- Clean compilation of eight Java source files with release 25.
- Executable Spring Boot JAR packaging through `mvn clean verify`.
- Class-file major version 69.

## Verification limits

No test sources exist, so successful Maven verification is not evidence of tested application behavior. Code coverage is unavailable. PostgreSQL-backed startup was not tested as part of this upgrade.

The direct-dependency vulnerability scanner stalled during authentication and returned no findings. Security verification remains incomplete. The framework version is retained from the existing application; this work does not claim that it is the latest supported Spring Boot release.
