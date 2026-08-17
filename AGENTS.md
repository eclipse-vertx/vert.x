# Agent Guidelines

Instructions for AI coding agents working in this repository.

## Build & Verify Commands

```bash
mvn test-compile         # compile code and tests
mvn test                 # run all tests (requires Docker for database interactions)
mvn spotless:check       # verify formatting
mvn spotless:apply       # auto-fix formatting
```

## General Coding Rules

These rules apply when **writing or modifying code**. Code review is the checkpoint where compliance is verified.

Please also make sure you adhere to the [code style guidelines](https://github.com/vert-x3/wiki/wiki/Vert.x-code-style-guidelines)

### API Design

- Public contracts are interfaces in the top package (`io.vertx.core`, `io.vertx.core.http`, ...)
- Expose construction via static factory methods, not constructors
- Implementations go in `impl/` subpackages
- Internal API are located in `ìnternal` package (`io.vertx.core.internal`, `io.vertx.core.http.internal`, ...)
- Public contracts should never declare elements using implementation or internal types
- Internal contracts should never declare elements using implementation

### Module Boundaries

`module-info.java` governs exports.
Implementation packages are exported only to their corresponding test modules, do not widen exports without discussion.
Test module descriptors (`src/test/java/module-info.java`) can be modified freely, e.g. to add a `requires` for a new dependency used in tests.

### Copyright Header

New Java files must include the dual-license header matching existing files:

```java
/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
```

The range is always `2011-[current year]`.

### Test Location

- **Unit tests**: Same module as code under test, in `src/test/java/`
- **Integration tests**: May be in separate test modules or `src/test/java/`

### Test Requirements

- All new features must include tests
- Any bug fix must include regression tests

## Development Workflow

### Incremental Development

When making changes:
1. Compile frequently: `mvn compile -pl <module>`
2. Run affected tests: `mvn test -pl <module>`
3. Verify formatting: `mvn spotless:check`
4. Run full build before PR: `mvn clean install`

### Build Optimization

```bash
# Skip tests during development
mvn compile -DskipTests
```

## Contribution Process

- All commits must be signed off: `git commit -s` (DCO)
- Commit messages should end with: `Assisted-by: [Provider] [Model-Family] ([Version/ID])` (replace placeholders)
- Contributors must have signed the [Eclipse Contributor Agreement (ECA)](https://www.eclipse.org/legal/ECA.php)

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full contribution workflow.

## Code Review Guidelines

### Verify

- General coding rules above are followed
- Test coverage is present
- No breaking changes to public interfaces without prior discussion

### Do Not Comment On

- Patterns already used consistently throughout the codebase
