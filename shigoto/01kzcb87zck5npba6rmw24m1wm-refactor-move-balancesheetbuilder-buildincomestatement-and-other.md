---
title: 'refactor: re-organize directories'
status: todo
order: k
---

`ui` sub-directories should only contain ui-directly-related files such as 
*Adapter, *Fragment, *ViewModel, *ViewModelFactory.

Other domain-focused logic should be moved to feature-based directories 
under a `core` directory.

Also move *Dao, model, *Repository files to this new `core` directory, to 
appropriate feature-based directory.