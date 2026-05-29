# Productivity App

A goal-tracking app that helps users achieve their objectives through structured habits and tasks.

## Core Concepts

### Goals

A **goal** is an outcome the user wants to achieve (e.g., "Save $1,000"). Goals have a target date by which the user intends to achieve them.

Goals are achieved by associating one or more habits or tasks with them.

### Habits

A **habit** is a recurring action the user commits to in order to progress toward a goal (e.g., "Set aside $100 every week").

Habits repeat on a defined schedule. Supported recurrences:
- **Weekly** — repeats every week
- **Every two weeks** — repeats every two weeks, with a one-week execution window
- **Monthly** — repeats every month

When a habit is linked to a goal, the app automatically generates **occurrences** — one per repetition needed to reach the goal. For example, a weekly $100 habit tied to a $1,000 goal produces 10 occurrences spread over 10 weeks.

### Occurrences

An **occurrence** is a single scheduled instance of a habit. Each occurrence has a time window during which it should be completed (e.g., Monday through Sunday for a weekly habit).

Occurrences can be marked as **done** once the user has completed them.

### Tasks

A **task** is a one-time action the user needs to execute (as opposed to a recurring habit). A task has an optional description, an optional estimated duration, and can optionally be associated with a goal.

A task's status can be:
- **Not started** — the default state
- **In progress** — the user has started working on it
- **Done** — the user has completed it
