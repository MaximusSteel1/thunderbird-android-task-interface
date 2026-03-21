# TaskMail Android Phase 0 Public Plaintext Baseline (v1)

Updated: 2026-03-21

## Status

This note freezes the Android-side direct-connect baseline used to close the planning-only part of Phase 0.

It does not claim that Android already implements or validates this runtime path end to end.

## Related Docs

- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-authority-v0.1.md`
- `docs/taskmail/planning/android/taskmail-android-public-plaintext-direct-connect-plan-v0.1.md`
- `docs/taskmail/planning/android/taskmail-next-session-handoff-2026-03-21-public-plaintext-direct-connect.md`
- `E:\projects\mail_based_task_manager\docs\plans\phase0_relay_readiness_note.md`

## Frozen Baseline

The current Android-side direct-connect baseline is:

- public IP: `124.223.41.153`
- configured port: `8787`
- diagnostic endpoint: `http://124.223.41.153:8787/healthz`
- connection endpoint: `ws://124.223.41.153:8787/relay`
- transport auth: token-based bootstrap/auth admission
- token boundary: Android may hold the transport token locally for direct-connect bootstrap, but the token must never be
  echoed in docs, logs, screenshots, or mail bodies
- fallback rule: if host, port, token, connect, or `hello -> hello_ack` bootstrap fails, the user flow remains on the
  current mail path rather than pretending the direct path is available

## Current Shared Readiness Reading

The adjacent PC/VPS readiness note currently records that the inspected public deployment still appears TLS-backed
rather than already serving this plaintext baseline.

That is a deployment-state judgment.
It does not reopen the Android-side planning reset or the frozen baseline itself.

## Phase 0 Closeout Judgment

For the Android-side planning layer, Phase 0 is now closed by:

- the new authority note
- the staged phase plan
- the cleanup of conflicting Android planning docs
- this exact baseline freeze
- the current handoff that points the next session at Phase 1

The next active Android phase is Phase 1: bootstrap promotion and reusable connection seam.
