# Passbird Updater #

Passbird Updater automatically downloads the latest version of [Passbird](https://github.com/christianpflugradt/Passbird/releases).

As of May 22, 2026, Passbird releases are published via GitHub Releases instead of the former website download location. This updater now uses GitHub release metadata and downloads the `passbird.jar` release asset from the latest public Passbird release by default.

This repository contains the updater source code. Build the updater jar locally with:

```shell
./gradlew jar
```

# How to start

The script [update-and-run-passbird.sh](update-and-run-passbird.sh) can be used to run Passbird Updater and afterward start Passbird itself. The script is independent of the operating system in use as long as Java is installed. Be sure to replace the placeholders.

# How it works

To detect local versions of Passbird, Passbird Updater requires the Passbird jar directory as an argument. In that directory Passbird Updater will save the latest version `x.y.z` or `x.y.z-dev.YYYYMMDD.N` as `passbird-x.y.z.jar` or `passbird-x.y.z-dev.YYYYMMDD.N.jar`. If the file already exists locally it won't be downloaded again. The latest version will then be copied to `passbird.jar`, which you may use to start Passbird itself.

The updater defaults to the `stable` channel. On that channel it queries the latest public release from the [Passbird GitHub Releases page](https://github.com/christianpflugradt/Passbird/releases), reads the release version and `passbird.jar` asset URL from the GitHub API, downloads the jar, and verifies it against the release asset digest published by GitHub before updating the local `passbird.jar`.

As an optional parameter you may specify the number of version to keep. If an update is available and has been successfully downloaded, Passbird Updater will remove old local versions of Passbird. If no value is passed the number of versions to keep defaults to 3, so if 5 versions of Passbird are present locally after downloading the latest, the 2 oldest versions will be deleted.

As another optional parameter you may pass `--channel dev` to opt into development prereleases. Development prereleases use versions such as `6.4.0-dev.20260525.1` and are not selected unless the `dev` channel is requested explicitly.

The following example for Passbird Updater assumes that `passbird.jar` is located in `/opt/passbird/` and the last `4` versions of Passbird should be kept:

```shell
java -jar passbird-updater.jar /opt/passbird/ 4
```

The following example opts into development prereleases while keeping the default number of local versions:

```shell
java -jar passbird-updater.jar /opt/passbird/ --channel dev
```

# How to update the updater

Passbird Updater itself is not versioned and always keeps the same jar file name `passbird-updater.jar`. On startup it checks the latest published Passbird Updater release metadata on GitHub, compares the published asset digest with the checksum of the currently running local jar file, and if they differ it prints a direct download link to the newer updater release.

This check does not download the remote updater jar file. It only reads GitHub release metadata and compares it with the checksum of the local jar file in use.

I can imagine only three likely reasons Passbird Updater itself would be updated:
1. Passbird releases move away from GitHub Releases
2. Passbird downloads are not provided as platform independent jar files anymore
3. Passbird Updater contains a bug that should be fixed

In all three cases respectively if you are affected by the bug, your instance of Passbird Updater may need to be replaced manually. If that is the case, use the link printed by Passbird Updater, visit this repository, retrieve the latest version of Passbird Updater or open an issue here to get support.

# Why it is not part of Passbird

Passbird is 100% offline as one of its primary design goals.

Automatically updating Passbird poses a security risk as domain hijacking or man-in-the-middle attacks might go unnoticed. If the user is required to manually check the [Passbird releases page](https://github.com/christianpflugradt/Passbird/releases) for updates, they might detect malicious circumstances more easily than an open source updater that attackers can study before attempting to exploit the user.

Passbird Updater will also download any new version immediately when it's next run, while users might take a while to update to the latest version manually. If an attack happens fewer users are likely to be affected before it is mitigated, if they update Passbird manually rather than using the Passbird Updater.

Passbird Updater should be understood as a comfort function at the cost of security. If you're fine with it, feel free to use it, but be aware of the risks. As a user you alone are responsible for using Passbird Updater.
