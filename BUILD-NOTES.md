# Koog skill review and Devoxx build evidence

## Two skill consumers

The coding agent used the installed Koog authoring guidance to build j-claw.
Running j-claw discovers the independent corporate-speak runtime skill. These
are the two halves of the round-4 meta-moment.

## Sources used

- Installed Tessl manifest: jbaruch/koog **0.5.1**, with
  author-strategy, use-agent-skills and domain-model-subtask-pipeline guidance.
- [Koog 1.3.0 tagged source](https://github.com/JetBrains/koog/tree/1.3.0),
  commit `3acc88cf8ce70b87d8afbd3cf184844a50aa504e`.
- Native API signatures and deterministic graph tests, rather than copying
  uncompiled plugin examples.
- [Plugin refresh issue #31](https://github.com/jbaruch/koog-plugin/issues/31)
  records invalid API examples and guidance requiring correction. It remains open.

The local review found stale imports and configuration examples, outdated memory
guidance, incomplete CLI credential assumptions and stale model profiles. Tagged
source took precedence where guidance disagreed. This build does not imply that
the plugin has already been repaired.

## Resulting changes

- Upgrade Koog runtime and beta modules to 1.3.0 / 1.3.0-beta.
- Use native built-in Gemini profiles rather than cloning an older profile.
- Carry the current typed request through each critic invocation.
- Keep automatic review separate from human confirmation and delivery.
- Bind a raw validated MCP receipt to the exact approved target/message and attempt.
- Write sent history only after confirmation, using the actual request's target.
- Restrict native runtime file tools to the configured skills root, including
  absolute, parent-traversal and symlink cases.
- Clean up already-started MCP processes if later startup fails.
- Replace the IdeaConf branch-switching/reset launcher with explicit Devoxx modes.

## Verification boundary

`./gradlew :app:test :mocks:mcpJars` compiles the application and mocks and runs
deterministic tests without paid provider calls. The tests cover the typed review
loop, human retry constraints, approval gates, CLI schema parsing, receipt validation,
memory extraction, skill discovery/file scope and trace metadata.

Live provider transports, Langfuse export, full terminal rehearsal and the Port
epilogue remain pending. Complete the app first, then derive step branches by
removing features. Native task/verification helper
teaching examples still need building before the custom CLI walkthrough.
