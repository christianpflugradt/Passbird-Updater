# Working on Passbird Updater

- The jar keeps the fixed name `passbird-updater.jar`: the update notice compares its checksum with the released asset.
- Never run `./gradlew dependencyCheckAnalyze` locally unless asked; the daily `dependency-audit.yml` workflow audits the runtime classpath.
