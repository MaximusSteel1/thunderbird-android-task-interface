# TaskMail GitHub Sync Workflow

This document is the current working workflow for continuing this fork's TaskMail development across multiple
computers.

As of 2026-03-21:

- Active development branch: `taskmail-dev`
- Remote: `origin = https://github.com/MaximusSteel1/thunderbird-android-task-interface`
- Do not use `origin/main` as the day-to-day TaskMail development branch for this fork

`origin/main` and the current TaskMail development line are not the branch you should use for normal `pull / push`
iteration. Use `taskmail-dev` unless you intentionally create a short-lived child branch from it.

## First-Time Setup On A New Computer

Clone the repository and switch to the active TaskMail branch:

```powershell
git clone https://github.com/MaximusSteel1/thunderbird-android-task-interface
cd thunderbird-android-task-interface
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git status --short --branch
```

Expected result:

- current branch is `taskmail-dev`
- upstream is `origin/taskmail-dev`
- working tree is clean

If you plan to build on the new machine, also set Java 21 before running Gradle:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
```

## Daily Start On Either Computer

Before writing code, make sure you are on the shared branch and up to date:

```powershell
git status --short --branch
git pull --rebase origin taskmail-dev
```

If `git status` shows a dirty tree before you pull, stop and either commit your local work first or stash it
deliberately.

## Standard Two-Computer Handoff

### Stop On Computer A

When you want to move work to another computer, do not leave important changes only in the working tree.

```powershell
git status
git add -A
git commit -m "chore(taskmail): checkpoint current workspace state"
git push origin taskmail-dev
```

Notes:

- Replace the commit message with a more specific `feat:` or `fix:` message when the checkpoint is a real logical step.
- Use `chore(taskmail): checkpoint ...` only when you need a safe sync point and the work is still in progress.
- Do not rely on `git stash` as your primary cross-computer handoff method.

### Resume On Computer B

```powershell
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git status --short --branch
```

Then continue working normally.

## End-Of-Session Routine

At the end of each session, use this checklist:

1. Run `git status`
2. Commit the work you want to keep
3. Push `taskmail-dev`
4. Confirm GitHub shows the latest commit

Recommended commands:

```powershell
git status
git add -A
git commit -m "feat(taskmail): <short summary>"
git push origin taskmail-dev
```

## If You Need An Experimental Branch

If you want to try a risky refactor without disturbing the shared branch:

```powershell
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git checkout -b taskmail-dev-experiment
git push -u origin taskmail-dev-experiment
```

Use short-lived branches for experiments only. Merge or cherry-pick the good work back into `taskmail-dev`, then keep
`taskmail-dev` as the main sync branch across machines.

## Conflict Handling During Pull

If `git pull --rebase origin taskmail-dev` reports conflicts:

1. Open the conflicted files
2. Resolve the conflict markers
3. Stage the resolved files
4. Continue the rebase

Commands:

```powershell
git status
git add <resolved-files>
git rebase --continue
```

If the rebase became confused and you want to back out:

```powershell
git rebase --abort
```

Then inspect the branch state before trying again.

## Files That Must Stay Local

The repository is configured to keep local development artifacts out of Git. Do not force-add them.

Ignored local-only paths currently include:

- `.android-user/`
- `.gradle-user/`
- `.tmp/`
- `_mailin_*`

These are machine-specific caches, debug data, or validation artifacts and should not be part of cross-computer sync.

## Safe Commands To Memorize

Most day-to-day work only needs these commands:

```powershell
git checkout taskmail-dev
git pull --rebase origin taskmail-dev
git status --short --branch
git add -A
git commit -m "feat(taskmail): <summary>"
git push origin taskmail-dev
```

## Commands To Avoid For Normal Sync

Do not use these casually on this fork's TaskMail work:

- `git push origin master:main`
- `git push --force`
- `git reset --hard` when you have not backed up your local work
- direct day-to-day work on `origin/main`

## Optional Improvement

If you want cloning this repository on future machines to land on the correct branch more naturally, change the GitHub
default branch of this fork from `main` to `taskmail-dev` in the repository settings. This is optional, but it reduces
mistakes.
