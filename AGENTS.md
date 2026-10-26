# AGENTS.md

## Versioning

The version describes the effect on the **output files**, not on the code. It is `MAJOR.MINOR`, set only in
`version` in `build.gradle.kts`; the build generates `TOOL_VERSION` from it, and every encode writes it into the
video stream as `FFMPEG_COMMANDS_VERSION`.

- **Major:** the video encoding changes (`ENCODER_OPTIONS`, `QUALITY_OPTIONS`, GOP, deinterlace filter). Existing
  encodes become outdated and worth re-encoding. Resets minor to 0.
- **Minor:** any other change to the output file (audio or subtitle mapping, attachments, metadata, ...).
- **No bump:** the output file stays the same apart from the tag: fixes, refactoring, Docker wrapping, CLI options,
  error messages, dependency and toolchain updates.

When a change touches `src/main/` or the build logic (`build.gradle.kts`, `settings.gradle.kts`,
`gradle.properties`, the Gradle wrapper), propose the bump (major, minor or none, with a one-line reason) and wait for
the user's confirmation before changing `version`. With a bump, also update the literal version pinned in
`VersionTagTest`. Changes to README, docs or tests alone need no bump.

## Coding rules

- Curly braces on every `if`, `else`, `for` and `while` body, even single statements.
- One top-level type per file; nested classes and companion objects are fine.
- Every constructor parameter on its own line, even when there is only one.
- Argument and parameter lists end without a trailing comma.
- TDD: write the failing test first, then the code. New features come with tests.
- Dummy file names only (`movie.mkv`, `somefile.mkv`) in tests, docs and commits; no real titles, usernames or paths.
