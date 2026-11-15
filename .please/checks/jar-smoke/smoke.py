"""Build the updater jar and start it without arguments; it must exit 1 with its usage hint."""

import subprocess
import sys

JAR = "build/libs/passbird-updater.jar"


def main():
    subprocess.run(["./gradlew", "jar"], check=True, capture_output=True)
    result = subprocess.run(["java", "-jar", JAR], capture_output=True, text=True, timeout=60)
    if result.returncode == 1 and "Passbird directory not specified!" in result.stdout:
        print("PASS: the jar starts and reports the missing Passbird directory")
        return 0
    print(f"FAIL: unexpected exit status {result.returncode} or output from {JAR}")
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:  # A crash is a technical failure, never a FAIL verdict.
        print(f"ERROR: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(2)
