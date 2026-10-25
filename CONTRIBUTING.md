# Contributing

Use Java 17 and the included Gradle wrapper. Run `./gradlew build` (or `gradlew.bat build` on Windows)
before submitting a change. Start the development client with `runClient`; use `-PwithoutEmi` to
check the optional-integration path.

Changes must preserve the client-only design: no required server installation, custom server search
packets, or assumptions that unopened inventories are available. Keep player inventory slots out of
container snapshots and highlights. EMI must remain optional. New menu integrations should be tested
against actual inventory synchronization and block association.

Describe the user-visible problem, resulting behavior, and validation in pull requests. For changes
to rendering, include genuine in-game screenshots. Document substantial AI-generated contributions
accurately, consistent with [AI_DISCLOSURE.md](AI_DISCLOSURE.md).

Code contributions are offered under the repository's CC0-1.0 license. Preserve applicable
third-party notices and identify separately licensed assets.
