# Repository instructions

## Current objective and document order

V1 is a bounded portfolio project for fresher backend/full-stack applications. The owner must understand and be able to maintain it. A working, well-explained small system is the completion target.

Before implementation, read:

1. [docs/v1-scope.md](docs/v1-scope.md): scope, exclusions and acceptance gates.
2. [IMPLEMENTED.md](IMPLEMENTED.md): current facts and missing evidence.
3. [ARCHITECTURE.md](ARCHITECTURE.md): V1 structure and contracts.
4. [docs/roadmap.md](docs/roadmap.md): the next checkpoint.
5. [docs/resume-evidence.md](docs/resume-evidence.md): evidence and owner review.

The latest explicit owner instruction takes precedence. Otherwise, the V1 scope governs implementation. `docs/target-architecture.md`, business documents and `docs/history/` describe later or superseded work. Do not treat their features as V1 tasks.

## Keep the scope fixed

- Implement the agreed checkpoint in small, reviewable steps. Do not build later business features as convenient extras.
- Keep V1 to the catalog/custom flows, one STL per revision up to 20 MB, one order line of quantity one, one part/plate job, sandbox payments and one simulated printer.
- Use one modular Spring Boot application and PostgreSQL. Keep external services behind a few explicit interfaces; avoid a generic framework or service split for hypothetical scale.
- Use the single primary CI pipeline and bounded tool demonstrations in the scope. Optional Ansible, Jenkins, RabbitMQ, Kafka and Kubernetes labs cannot delay acceptance or become runtime dependencies.
- Do not control real printers or activate real payments as part of V1.
- OneCreations remains a separate repository and site. Its UI restructure is not part of this repository's V1.

Resolve routine implementation choices within the documented scope. If a requirement would expand that scope, record the need and defer it; obtain an explicit new scope decision before implementing it.

## Code and verification standards

- Follow the feature-module layout and contracts in `ARCHITECTURE.md`. Use clear names and short methods, with comments explaining non-obvious decisions.
- Keep controllers thin, business rules in application/domain code, and persistence/external API details behind module boundaries. Do not expose persistence entities as request/response contracts.
- Enforce identity, role and resource ownership on the backend. Validate state transitions and input server-side.
- Use SQL constraints and transactions for invariants. Test concurrency and persistence against PostgreSQL where its behavior matters.
- Keep migrations versioned, secrets outside Git, customer files private and logs free of credentials or file contents.
- Preserve existing user changes. Run checks appropriate to the change and report actual results, including failures or checks that could not run.
- Do not report a planned command, an illustrative result or a dashboard screenshot as proof of passing behavior.

## Checkpoints, understanding and evidence

At each checkpoint:

1. Deliver the smallest complete behavior required by the scope.
2. Run and record its relevant checks with the code revision and environment.
3. Update `IMPLEMENTED.md` and link the evidence record.
4. Explain what changed, how to run it, what failure it handles and the known limits.
5. Leave the owner's explanation/change/debug review pending until Harsha actually completes it. This review checks personal understanding; it is not a restriction on continuing already-authorized implementation work.

An implementation can pass automated tests while its ownership review is still pending. Never mark that review, real production usage, deployment or a résumé claim complete by inference. Preserve the distinction between planned, implemented, tested, deployed and used in real production.

V1 acceptance requires all required gates, including the owner's review. Freeze the accepted release and prepare truthful résumé claims from evidence. Begin V2 only after acceptance, résumé update and an explicit decision on the next increment. A successful build alone does not authorize the whole target architecture.

## Authorship and publication

Harshavardhan M (`harsha135135`) is the sole credited contributor. Do not add automated-tool attribution to commits, pull requests or documentation. When publishing a project repository, use the owner's GitHub account: `https://github.com/harsha135135`.

Do not fabricate completion claims, traffic figures, performance improvements or business outcomes. Do not publish credentials, customer data or private model files as portfolio evidence.
