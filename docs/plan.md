# Implementation Plan

### Phase 1 — Foundation
- Set up Room database
- Set up MVVM scaffolding (base ViewModel, repository pattern)
- Basic navigation structure

### Phase 2 — Tasks
- `Task` entity + DAO
- Task list screen
- Create/edit task screen (name, description, estimated duration, status)

### Phase 3 — Goals
- `Goal` entity + DAO
- Goal list screen
- Create/edit goal screen (name, target date)
- Link tasks to a goal (optional)

### Phase 4 — Habits & Occurrences
- `Habit` + `Occurrence` entities + DAOs
- Occurrence auto-generation from recurrence rule + goal target date
- Habit creation screen (name, recurrence, linked goal)
- Occurrences list with mark-as-done
