# Passbird Updater #

Passbird Updater automatically downloads the latest version of [Passbird](https://gitlab.com/christianpflugradt/passbird).

It is available as download via the [Passbird website](https://pflugradts.de/password-manager/) or this [direct download link](https://pflugradts.de/downloads/passbird/passbird-updater.jar).

# How to start

The script [update-and-run-passbird.sh](update-and-run-passbird.sh) can be used to run Passbird Updater and afterward start Passbird itself. The script is independent of the operating system in use as long as Java is installed. Be sure to replace the placeholders.

# How it works

To detect local versions of Passbird, Passbird Updater requires the Passbird jar directory as an argument. In that directory Passbird Updater will save the latest version 'x.y.z' as `passbird-x.y.z.jar`. If the file already exists locally it won't be downloaded again. The latest version will then be copied to `passbird.jar`, which you may use to start Passbird itself.

As an optional second parameter you may specify the number of version to keep. If an update is available and has been successfully downloaded, Passbird Updater will remove old local versions of Passbird. If no value is passed the number of versions to keep defaults to 3, so if 5 versions of Passbird are present locally after downloading the latest, the 2 oldest versions will be deleted.

The following example for Passbird Updater assumes that `passbird.jar` is located in `/opt/passbird/` and the last `4` versions of Passbird should be kept:

```shell
java -jar passbird-updater.jar /opt/passbird/ 4
```

# How to update the updater

Passbird Updater itself is designed not to be updated. Hence, it is not versioned. I can imagine only three likely reasons Passbird Updater itself would be updated:
1. Passbird downloads move to another domain
2. Passbird downloads are not provided as platform independent jar files anymore
3. Passbird Updater contains a bug that should be fixed

In all three cases respectively if you are affected by the bug, your instance of Passbird Updater will no longer work. If that is the case, visit this repository, retrieve the latest version of Passbird Updater or open an issue here to get support.

# Why it is not part of Passbird

Passbird is 100% offline as one of its primary design goals.

Automatically updating Passbird poses a security risk as domain hijacking or man-in-the-middle attacks might go unnoticed. If the user is required to manually check the [Passbird website](https://pflugradts.de/password-manager/) for updates, they might detect malicious circumstances more easily than an open source updater that attackers can study before attempting to exploit the user.

Passbird Updater will also download any new version immediately when it's next run, while users might take a while to update to the latest version manually. If an attack happens fewer users are likely to be affected before it is mitigated, if they update Passbird manually rather than using the Passbird Updater.

Passbird Updater should be understood as a comfort function at the cost of security. If you're fine with it, feel free to use it, but be aware of the risks. As a user you alone are responsible for using Passbird Updater.
