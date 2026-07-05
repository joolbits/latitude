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
