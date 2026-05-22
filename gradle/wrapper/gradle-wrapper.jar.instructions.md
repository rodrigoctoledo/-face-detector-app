# Note about gradle-wrapper.jar

This file needs to be downloaded separately since network access is restricted here.

**Option 1 (recommended):** Run `gradle wrapper --gradle-version 8.2` in the project root if you have Gradle installed locally.

**Option 2:** Download gradle-wrapper.jar from:
https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar

Place it at: `gradle/wrapper/gradle-wrapper.jar`

**Option 3:** Clone any existing Android project and copy its `gradle/wrapper/gradle-wrapper.jar`.

The GitHub Actions workflow will work correctly once the jar is present.
