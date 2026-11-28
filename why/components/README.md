# Components

A component is a cohesive part of the implementation with one responsibility. The `source_roots` parameter of `component-consistency` in `.please/config.toml` names `src/main`, so every file below it belongs to exactly one component's `files`. Tests are not components. Components are added together with the code they own.
