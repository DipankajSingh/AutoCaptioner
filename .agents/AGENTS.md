# Workflow & Trust Safeguards

These rules are permanently injected into my system prompt for this specific project to prevent regressions in my behavior.

## Core Operations
1. **Task Artifacts & Transparency**: When executing an Implementation Plan, I MUST update the `task.md` checklist synchronously *immediately* after finishing a step, before taking any actions toward the next step. I must not batch updates at the end of the execution.
2. **Strict Git Hygiene**: I MUST run `git status` and actively review uncommitted changes in the working tree *before* executing any `git commit` commands.
3. **Handling Unrelated Changes**: If the user has uncommitted changes that are unrelated to my current task or plan, I MUST STOP and explicitly ask the user how they want to handle those changes (e.g., commit them separately, stash them, or bundle them) before I proceed.

## Debugging & Communication
4. **Holistic (Top-Down) Debugging**: When investigating bugs, edge cases, or issues, I MUST NOT get tunnel vision on the specific screen or file where the error manifests (e.g., trial-and-error fixing in the Export screen). I must trace the data and logic from the very top of the user flow (e.g., Home Screen -> Editor -> Export). Gathering broader context prevents band-aid fixes and uncovers the true root cause faster.
5. **Anti-Assumption & Questioning**: I MUST NEVER make blind assumptions when requirements or constraints are ambiguous. I MUST NEVER ignore questions asked by the user. If a bug report or request is vague (e.g., "export is broken"), I MUST NOT waste time guessing or investigating all possible paths to figure out what the user meant. Instead, I MUST immediately stop and explicitly ask the user for clarification (e.g., "Which specific screen are you on?", "Which flow are you using?", "How exactly did it fail?").
6. **Prioritize Direct Diagnostics over Guesswork**: If the absolute best method to diagnose an issue is blocked (e.g., device is disconnected, missing a crash log), I MUST immediately stop and ask the user to clear the roadblock (e.g., "Please connect your device", "Please run the app so I can see the log"). I MUST NOT silently give up on the optimal diagnostic path and resort to inefficient guesswork, trial-and-error, or fallback methods (like blindly reading code or caches).

## Development & Engineering Standards
7. **Impact Analysis (Blast Radius)**: Before modifying, moving, or deleting any function, method, or component, I MUST systematically search the codebase to identify all usages. I must evaluate how the change affects other features. If a change alters existing behavior elsewhere, I MUST ask the user for confirmation before proceeding.
8. **Code Reusability (DRY Principle)**: Before writing a new function, class, or UI component, I MUST search the codebase to check if identical or similar logic already exists (even in unrelated screens). If it exists, I should propose reusing it or extracting it into a shared component (if it makes logical sense, without forcing it). When in doubt, I will ask the user for the preferred approach.
9. **Deprecation & Modern API Usage**: I MUST NEVER knowingly use deprecated methods or classes with an "it's fine for now" mindset. I must proactively verify that APIs are current. I will always seek and implement the most optimal, modern algorithms and approaches available.
10. **UX & Research Driven Implementation**: When building new screens, components, or designs, I MUST use my web search tools to research current industry standards, user-approved best practices, and efficient patterns. I must prioritize premium UX, avoid anti-patterns, and write clean, necessary logic.
11. **Database & Migration Safety**: Any time I modify database entities, DAOs, or repositories, I MUST evaluate the impact on existing data. I must actively check if a schema migration is required and explicitly address database versioning and migration safety before making the change.

## Product Vision & User Perspective
12. **The User Lens**: When implementing features or making technical decisions, I MUST filter them through the core product vision: providing frictionless, instant auto-captioning for short-form creators. I must prioritize perceived performance (e.g., masking transcription time with fluid UX) and avoid over-engineering technical solutions (like "on-device ML") if they do not directly improve the user's workflow, speed, or wallet.

## Strict Modification Approval
13. **Mandatory Fix Proposal Before Coding**: If the user reports a bug or requests an investigation into something broken, I MUST investigate and report my findings. I MUST then explicitly outline the fixes I intend to make and ask for the user's permission. I MUST ALWAYS wait for the user's explicit approval BEFORE making any actual code modifications or writing any code.
