# Passbird Updater #

[![build](https://github.com/christianpflugradt/Passbird-Updater/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/christianpflugradt/Passbird-Updater/actions/workflows/ci.yml) [![release](https://img.shields.io/github/v/release/christianpflugradt/Passbird-Updater?display_name=tag&sort=semver)](https://github.com/christianpflugradt/Passbird-Updater/releases) [![license](https://img.shields.io/badge/license-Apache%202.0-3949ab.svg)](https://opensource.org/licenses/Apache-2.0)

[![tests](https://christianpflugradt.github.io/badges-and-stuff/passbird-updater/tests.svg)](https://christianpflugradt.github.io/badges-and-stuff/) [![branch coverage](https://christianpflugradt.github.io/badges-and-stuff/passbird-updater/branch-coverage.svg)](https://christianpflugradt.github.io/badges-and-stuff/) [![instr. coverage](https://christianpflugradt.github.io/badges-and-stuff/passbird-updater/instruction-coverage.svg)](https://christianpflugradt.github.io/badges-and-stuff/) [![LOC](https://christianpflugradt.github.io/badges-and-stuff/passbird-updater/loc.svg)](https://christianpflugradt.github.io/badges-and-stuff/)

Passbird Updater automatically downloads the latest version of [Passbird](https://github.com/christianpflugradt/Passbird/releases).

As of May 22, 2026, Passbird releases are published via GitHub Releases instead of the former website download location. This updater now uses GitHub release metadata and downloads the `passbird.jar` release asset from the latest public Passbird release by default.

This repository contains the updater source code. Build the updater jar locally with:

```shell
./gradlew jar
```

The Statistics workflow runs the unit tests, measures their branch and instruction coverage with JaCoCo, counts the lines of production code in `src/main` with scc and sends the results to [badges-and-stuff](https://github.com/christianpflugradt/badges-and-stuff), which renders the badges above.

This repository documents its requirements, decisions and components in [why](why/README.md).

# How to start

The script [update-and-run-passbird.sh](update-and-run-passbird.sh) can be used to run Passbird Updater and afterward start Passbird itself. The script is independent of the operating system in use as long as Java is installed. Be sure to replace the placeholders.

# How it works

To detect local versions of Passbird, Passbird Updater requires the Passbird jar directory as an argument. In that directory Passbird Updater will save the latest version `x.y.z` or `x.y.z-dev.YYYYMMDD.N` as `passbird-x.y.z.jar` or `passbird-x.y.z-dev.YYYYMMDD.N.jar`. If the file already exists locally it won't be downloaded again. The latest version is verified and copied to `passbird.jar`, which you may use to start Passbird itself, also when that version was already present locally.

The command line syntax is:

```shell
java -jar passbird-updater.jar <passbird-jar-directory> [versions-to-keep] [--channel stable|dev]
```

The Passbird jar directory must be the first argument. The release channel must be passed as `--channel dev` or `--channel stable`, not as a bare positional value such as `dev`.

The updater defaults to the `stable` channel. On that channel it queries the latest public release from the [Passbird GitHub Releases page](https://github.com/christianpflugradt/Passbird/releases), reads the release version and `passbird.jar` asset URL from the GitHub API, downloads the jar, and verifies it against the release asset digest published by GitHub before updating the local `passbird.jar`.

As an optional parameter you may specify the number of versions to keep, which must be at least 1. After `passbird.jar` has been updated, Passbird Updater will remove old local versions of Passbird, development and stable versions counted together. If no value is passed the number of versions to keep defaults to 3, so if 5 versions of Passbird are present locally after downloading the latest, the 2 oldest versions will be deleted.

As another optional parameter you may pass `--channel dev` to opt into development prereleases. Development prereleases use versions such as `6.4.0-dev.20260525.1` and are not selected unless the `dev` channel is requested explicitly.

The following example for Passbird Updater assumes that `passbird.jar` is located in `/opt/passbird/` and the last `4` versions of Passbird should be kept:

```shell
java -jar passbird-updater.jar /opt/passbird/ 4
```

The following example opts into development prereleases while keeping the default number of local versions:

```shell
java -jar passbird-updater.jar /opt/passbird/ --channel dev
```

If the latest version cannot be determined, downloaded or verified, Passbird Updater prints an error message and exits with status 1.

# How to update the updater

**You do not need to keep Passbird Updater up to date.** It is deliberately tiny, does one job and its behavior rarely changes. An installed updater keeps working as long as Passbird is published as `passbird.jar` on GitHub Releases.

The updater has no third-party libraries. It reads the release metadata of GitHub with a small parser of its own and relies on the Java runtime you start it with for HTTPS, checksums and file handling. The jar only bundles the Kotlin standard library. Keeping your Java runtime up to date matters far more for security than replacing the updater jar.

A new updater release is published when something changes for users, which is expected to be rare:

1. Passbird releases move away from GitHub Releases.
2. Passbird downloads are not provided as platform independent jar files anymore.
3. Passbird Updater contains a bug or a security issue that should be fixed.

Releases are also cut automatically once a month if changes such as a refreshed Kotlin version were made in between. Such a release changes nothing for you. If nothing releasable happened, no release is published; updates of the CI workflows and of the build tooling do not count as releasable. Read the [release notes](https://github.com/christianpflugradt/Passbird-Updater/releases): if they do not describe a problem you are affected by, keep using your current copy. Replacing the jar every few years is reasonable hygiene, not a necessity. No software is free of vulnerabilities, but the attack surface of a tool this small is correspondingly small.

On startup Passbird Updater checks the latest published Passbird Updater release metadata on GitHub, compares the published asset digest with the checksum of the currently running local jar file, and if they differ it prints a short manual-update notice together with a direct download link. The notice only means that a newer release exists, not that you must update. The check does not download the remote jar; it only reads GitHub release metadata and compares it with the checksum of the local jar file in use.

To replace your copy, use the link printed by Passbird Updater or visit this repository and retrieve the latest release. Open an issue here to get support.

# Why it is not part of Passbird

Passbird is 100% offline as one of its primary design goals.

Automatically updating Passbird poses a security risk as domain hijacking or man-in-the-middle attacks might go unnoticed. If the user is required to manually check the [Passbird releases page](https://github.com/christianpflugradt/Passbird/releases) for updates, they might detect malicious circumstances more easily than an open source updater that attackers can study before attempting to exploit the user.

Passbird Updater will also download any new version immediately when it's next run, while users might take a while to update to the latest version manually. If an attack happens fewer users are likely to be affected before it is mitigated, if they update Passbird manually rather than using the Passbird Updater.

Passbird Updater should be understood as a comfort function at the cost of security. If you're fine with it, feel free to use it, but be aware of the risks. As a user you alone are responsible for using Passbird Updater.
