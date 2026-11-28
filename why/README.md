# Why

This directory holds the Why of Passbird Updater: its requirements, decisions and components. It follows the [Why documentation approach](https://github.com/please-behave/why), whose purpose and principles are not repeated here. New requirements of the owner are recorded here first; code, tests and documentation follow in the same change. The [README](../README.md) is the user documentation: it may repeat what a user needs to install and run the updater, and where it differs from the Why, the Why wins.

## The product

Passbird Updater downloads the latest release of [Passbird](https://github.com/christianpflugradt/Passbird/releases) from GitHub, verifies it and makes it available as `passbird.jar`. It is a command line tool for users who accept a comfort function at the cost of security ([DEC-0001](decisions/DEC-0001.yaml)).

### Principles

- **Tiny and stable.** It does one job, has no third-party libraries and is developed further only for substantial reasons ([DEC-0003](decisions/DEC-0003.yaml)). Installed copies keep working as long as Passbird is published as `passbird.jar` on GitHub Releases.
- **Verify before trusting.** Nothing becomes `passbird.jar` without a matching checksum. The checksum is published by the same source as the jar, so it detects damaged or mismatching downloads, not a compromised release.
- **The user stays in control.** The updater never replaces itself and only points to a newer release of itself ([DEC-0002](decisions/DEC-0002.yaml)).
- **Released without effort.** Releasable changes are published by a monthly scheduler, never by a manual decision ([DEC-0005](decisions/DEC-0005.yaml)).
- **Honest about failure.** A failed update is reported and ends with a non-zero exit status ([DEC-0004](decisions/DEC-0004.yaml)).

### What it is not

- **Not part of Passbird.** Passbird stays offline.
- **Not a self-updating tool.** New updater releases are installed manually.

### Terms

- **Channel**: `stable` selects public releases, `dev` also considers development prereleases.
- **Passbird directory**: the directory that holds `passbird.jar` and the versioned copies.

## Artifacts

| Artifact | Purpose |
|---|---|
| **[Requirement](requirements/README.md)** | A binding, verifiable condition the updater must satisfy. |
| **[Decision](decisions/README.md)** | A significant choice with its context and rationale. |
| **[Component](components/README.md)** | A cohesive part of the implementation with one responsibility, its files and its boundaries. |

## References

References point from the more volatile artifact to the more stable one:

- a requirement names the decisions that shaped it,
- a component names the requirements it implements and the components it depends on,
- a decision names only the decisions it supersedes.

IDs are permanent and never reused. Decisions stay authoritative unless a newer decision supersedes them. Requirements stay authoritative unless marked `obsolete: true`.
