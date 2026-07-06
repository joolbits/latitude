# CLAUDE.md

Claude Code project guidance. (Codex-native agent instructions live in `AGENTS.md`; the
Latitude binder/evidence workflow rules in `AGENTS.md` apply to Claude too.)

## Orchestrating subagents that run LONG shell commands (READ BEFORE DELEGATING)

A subagent spawned via the `Agent` tool is NOT the main loop. It has **no way to sleep its
turn and be auto-re-invoked when a background job finishes.** `run_in_background: true` Bash
and the `Monitor` tool both *say* "keep working, you'll be notified" — but that notification
only wakes the **top-level** session, never a subagent. When a subagent backgrounds a long
job and then tries to "wait," its turn just ends with a non-report ("I'll stop and wait for
the notification…"), the task shows as *completed* to the coordinator, and the real work is
still running unsupervised. The only recovery is manually `SendMessage`-ing the agent to
resume it — sometimes twice. (Observed this session on the ~19-min gradle atlas run and the
~7.6-min headless-server + Spark capture; each needed two coordinator nudges.)

**Rule: subagents must run long commands in the FOREGROUND and block on them.**

When you prompt a subagent whose job includes a command that takes more than ~2 minutes,
put this in the prompt verbatim:

> Run the long-running command in the FOREGROUND (do NOT pass `run_in_background: true`, and
> do NOT use the `Monitor` tool). Set the Bash `timeout` parameter generously (e.g.
> `timeout: 1200000` for ~20 min, `timeout: 600000` for ~10 min — the max is 600000ms per
> call, so for jobs longer than 10 min run the work as a single command that finishes inside
> that window, or split it into foreground chunks each under the cap and block on each in
> turn). The tool call itself will block until the command exits, then you continue naturally
> and write your report. Never end your turn "to wait for a background notification" — you
> will not receive one; your turn will just be marked complete with no findings.

Why foreground works and backgrounding doesn't, for a subagent: a foreground Bash call keeps
the subagent's single turn open until the command returns, so the agent resumes with the real
output in hand. Backgrounding hands the wait to a notification channel the subagent isn't
wired to, so it strands.

**Coordinator-side mitigations** (until/if the harness changes):
- Prefer giving a long job to the top-level session, or to a foreground-blocking subagent as
  above, rather than a subagent that backgrounds + waits.
- If a subagent reports "completed" with a non-report like *"I'll wait for the background
  notification"*, that is the failure signature — `SendMessage` it to check its background
  job's status and deliver the real report; don't treat the task as done.
- The per-call Bash `timeout` cap is 600000ms (10 min). A single job that genuinely needs
  longer than 10 min of uninterrupted foreground wait can't be fully blocked-on in one call;
  for those, either run it from the top-level session (which *can* background + get woken), or
  design the subagent's command to checkpoint/exit under the cap and be resumed.

## Multi-step sequential background pipelines (nohup/driver-script pattern) -- NEVER delegate this to a subagent

Observed 2026-07-06: a subagent tasked with "before/after" regression proof (run A, apply a fix,
run B, compare) needed several long headless atlas generations with a `git stash pop` in between.
Since a single foreground Bash call can't span multiple separate commands with a state change
(the stash pop) injected between them, it wrote a `nohup`-backed driver shell script to chain the
whole sequence unsupervised -- reintroducing the exact "subagent backgrounds a job and can't be
woken" failure above, just one level up (now it's waiting on a *script*, not a single command).
When the runs took longer than expected, it retried/relaunched rather than waiting, and the
retries piled up as concurrent, overlapping processes.

**Rule: if a task needs N long-running steps with any state change between them, the
orchestrator runs each step, not the subagent.** Give a subagent ONE bounded foreground command
per turn; when it needs a second step after a state change (stash pop, flag toggle, etc.), have
it stop and report, then the orchestrator (which *can* reliably use `ScheduleWakeup` and get
woken) sequences the next step itself -- either directly, or by re-invoking the subagent for
exactly that one next bounded step. Do not accept "I'll write a driver script to run all N steps
and wait for it to finish" from a subagent; that is the same anti-pattern with extra steps.

## Shared, lockable resources -- never run two headless world-gen processes concurrently

`run-headless/world` (and similarly any single on-disk world directory the atlas/headless
tooling writes to) is locked per-instance by Minecraft (`session.lock`). Two concurrent
`runBiomePreview`/`atlas_runner.py` invocations against the *same* world directory don't fail
loudly -- they contend for the lock and both slow to a crawl, which looks exactly like a hung
process rather than a resource conflict. Before starting a new headless run, confirm no prior one
against the same output path is still alive (`ps aux | grep -i runBiomePreview`); never let a
retry/relaunch stack on top of a still-running prior attempt.

## Before estimating how long a command will take, check whether you already ran it this session

A `-Dlatitude.geoV2.enabled=true` atlas run at `--size regular --step 32` takes ~28 minutes
regardless of whether `-Dlatitude.atlasTerrainAware` is set -- the real cost is GeoAuthority
sampling per column on a large world, not terrain-awareness. An estimate given without checking
prior timing in the same session (assuming "drop one flag = fast") was wrong here and fed a bad
instruction to a subagent, contributing to the mess above. `grep` your own recent Bash output /
this file's history for the exact command before estimating; don't guess by category.

## When something looks stuck, check real process state immediately, not after being asked twice

`ps aux` and file mtimes are cheap and instant. If a background job's timing looks off, check
actual OS-level state (is the process alive? CPU%? file timestamps changing?) before assuming
either "it's fine, still waiting" or spending more turns coaching the subagent through it. This
would have caught the concurrent-process contention above in seconds instead of over an hour.
