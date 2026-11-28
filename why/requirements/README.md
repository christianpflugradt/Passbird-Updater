# Requirements

A requirement is one binding, verifiable statement about what the updater must do or be.

- **One statement.** It states one condition that can be verified on its own.
- **Observable.** It describes behavior or a property visible from outside, not a function, a type or a file.
- **Significant.** It stays true after a refactoring and its reason fits into one sentence, which is the `rationale`.
- **Code-free.** It never names code. Tests cite the requirements they verify in a comment such as `// REQ-0001`.
- **Stated once.** A fact that another source defines is referred to, not copied.

Every requirement has an `area`. The areas are `update`, `integrity`, `retention`, `interface`, `supply-chain` and `distribution`. A change in behavior starts with the requirement and is committed together with code, tests and documentation.
