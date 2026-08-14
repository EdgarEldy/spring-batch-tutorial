## Branch

`feature/<name>`

## Task checklist

<!-- Paste the relevant section's checklist from README.md, checked off item by item. -->

## Commit summary

<!-- One line per commit, or a short summary of what changed and why. -->

## Test checklist

- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] E2E tests pass
- [ ] `mvn verify` is green for the whole suite

## Code review checklist

- [ ] No business logic in controllers, they call a service and wrap the result in `ApiResponse`
- [ ] Services follow the contract/implementation pattern (interface in `service/`, implementation in `service/impl/`)
- [ ] Every endpoint returns `ApiResponse<T>` (or `ApiResponse<PageResponse<T>>` for lists)
- [ ] Every Job/Step has the listeners it needs (SkipListener on fault-tolerant steps, StepExecutionListener for logging)
- [ ] `PayrollRun.status` and `JobExecution.status` are never confused in code or in API responses
- [ ] Commits are atomic (one file per commit) and follow Conventional Commits
