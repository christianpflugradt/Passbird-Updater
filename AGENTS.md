# Working on Passbird Updater

Passbird Updater is a small Kotlin command line tool that downloads the latest Passbird jar from GitHub Releases. Keep it small, simple and free of speculative features.

- Source is `src/main/kotlin`, tests are `src/test/kotlin`. Build with `./gradlew jar`; the jar keeps the fixed name `passbird-updater.jar` because the update notice compares its checksum with the released asset.
- Add dependencies only with a strong argument. Fewer dependencies mean fewer advisories for a tool that rarely changes.
- Get maintainer approval for the exact wording before changing user-visible text or README claims about security.
- Keep source, documentation and output in English.
- Work on `main`. Commit and push each coherent change; stop after a successful push. Never force-push.
- Git hooks are the validation gates: `pre-commit` checks conventions, `pre-push` runs the complete suite. Install them with `please sync --yes`. Run only focused tests such as `./gradlew test --tests '*PassbirdUpdaterTest*'` during development, never the whole suite by hand.
- Commits follow Conventional Commits with scopes from `.please/commit-scopes.txt`. Releases are cut by the Release workflow through Semantic Release; never tag or publish by hand.
- Pins of the governance revision and the Please Behave governor in `mise.toml` belong to Renovate. Do not bump them by hand.
- Tests stay offline and use mocked network responses.
