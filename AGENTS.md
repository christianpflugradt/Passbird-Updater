# Working on Passbird Updater

`why/` is the source of truth for requirements, decisions and components: read `why/README.md` and the artifacts relevant to the task before implementing. The README is user documentation and may repeat what users need; where it differs from the Why, the Why wins. A new requirement of the owner is recorded in the Why first; code, tests and documentation follow in the same change. Tests cite the requirements they verify in a comment such as `// REQ-0001`.

- The jar keeps the fixed name `passbird-updater.jar`: the update notice compares its checksum with the released asset.
- Never run `./gradlew dependencyCheckAnalyze` locally unless asked; the daily `dependency-audit.yml` workflow audits the runtime classpath.
