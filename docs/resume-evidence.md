# V1 evidence and résumé gate

Status: process defined; no implementation evidence yet · Updated: 2026-10-07

V1 should give Harsha concrete engineering work to explain in interviews. A small project can demonstrate code quality, SQL, security, debugging and delivery. The number of installed tools does not establish those skills, and completion does not guarantee a job.

[V1 scope](v1-scope.md) defines what to finish. [IMPLEMENTED.md](../IMPLEMENTED.md) records what exists. This document explains when that work supports a résumé claim.

## What counts as evidence

| Claim | Minimum useful proof |
|---|---|
| Built a Java backend | Readable modules, a runnable vertical slice, API examples and the ability to change a business rule |
| Used PostgreSQL | Versioned migrations, meaningful constraints/transactions and integration tests against PostgreSQL |
| Implemented authorization | A customer cannot access another customer's order/file; a customer cannot perform operator actions |
| Integrated payments | Verified test-mode webhooks; wrong signatures and duplicate delivery tested; browser redirects cannot mark an order paid |
| Built reliable job handling | A repeatable lost-acknowledgement or restart scenario that does not create a second simulated execution |
| Used Docker | A fresh local startup, persistent data across container restart and an explanation of images, ports, networks and volumes |
| Deployed on AWS | The tested revision runs in the small AWS environment, files remain private, IAM is scoped and cleanup is documented |
| Used Terraform | A reviewed plan and successful creation/recreation/cleanup of the same demo infrastructure; explain state and secret handling |
| Used Nginx | Working HTTPS and routing; explain where browser requests go and which services remain private |
| Built CI | A failing check blocks the build/release path; a successful revision produces a traceable artifact |
| Used Prometheus/Grafana | A few application metrics, one dashboard and a recorded example of diagnosing a failure |
| Tested recovery | Restore a backup and prove the restored order and its referenced model version are usable |

Screenshots can support a record, but they do not replace executable checks or an explanation. Keep evidence free of tokens, customer identifiers and private file contents. Use synthetic fixtures.

## Evidence record template

Keep one short record per checkpoint under `docs/evidence/` when real work begins. Create that directory only when there is evidence to store. Use filenames such as `YYYY-MM-DD-checkpoint-name.md`.

```text
Checkpoint / claim:
Date:
Code revision (commit, or base commit plus the reviewed uncommitted diff):
Environment and relevant tool versions:
Scenario and purpose:
Setup and exact command or user steps:
Expected behavior:
Actual result, including failures:
Logs/test report/demo recording (sanitized):
What was simulated or test-mode:
Known limitations:
Owner explanation/change/debug review: pending | completed with dated notes
Related IMPLEMENTED.md row:
```

Performance claims additionally need the workload, hardware, duration, concurrency, dataset size, measurement method and repeated results. Before/after improvements need a comparable baseline. No throughput, uptime, cost saving or success-rate figure belongs on the résumé merely because it was a design target.

## Personal understanding checkpoint

For each critical area in the scope, Harsha should be able to:

1. **Explain:** trace a request from the UI through authorization, business logic, SQL and any external call; explain why each tool is present.
2. **Change:** implement a small variation, such as a quote-expiry rule or a new validation condition, and update the relevant test.
3. **Debug:** locate and fix a seeded failure using the available logs, database state and metrics.
4. **Discuss limits:** say what V1 does not handle and when a more complex solution would become justified.

Documentation is allowed; memorizing framework APIs is not the objective. Harsha must lead the reasoning and modification. A generated walkthrough or an agent completing the exercise does not establish personal understanding. Record the outcome honestly and revisit weak areas before making the associated résumé claim.

## V1 acceptance, then résumé update

- [ ] Required [scope](v1-scope.md) checkpoints and their automated checks pass.
- [ ] A fresh checkout can follow the README to run the application and demonstrate the workflow.
- [ ] Happy path, rejected access, duplicate payment, uncertain start and recovery can be shown with saved fixtures.
- [ ] The AWS demo, CI, required tool exercises and restore evidence refer to a known code revision.
- [ ] Known limits clearly state sandbox payments and simulated printing.
- [ ] Harsha has completed the explanation/change/debug review for critical modules.
- [ ] Harsha accepts the release; record its revision and, when authorized, a release tag.
- [ ] Select a few strong résumé bullets based on the completed evidence.
- [ ] Update the actual résumé and project links; record the date in `IMPLEMENTED.md`.

These checkboxes are all pending at the planning stage. V1 is not “done” because these documents exist. Application acceptance comes first, then the résumé update; only after both do we agree the next V2 increment. Optional labs cannot hold this gate open.

## Example wording after verification

**The following are conditional examples, not current achievements.** Use a bullet only after the linked evidence and personal review exist. Shorten to two or three bullets that fit the role.

- Built a Java/Spring Boot order application with PostgreSQL, versioned model approvals and role-based customer/operator access.
- Implemented verified sandbox payment webhooks and durable job attempts; tested duplicate delivery and simulator restart after a lost acknowledgement.
- Containerized and deployed the application on AWS EC2 with private S3 storage, Nginx HTTPS and a GitHub Actions pipeline; reproduced the demo environment with Terraform.
- Instrumented job outcomes and API behavior with Prometheus and Grafana, and used the dashboard to diagnose an injected failure.

Keep **sandbox payment** and **simulated printer** qualifications visible in the project description. Do not describe V1 as operating a production print farm, serving paying customers or providing high availability.

Optional tooling can go in a separate hands-on section after its own exercise: for example, “Ran a local Kubernetes deployment and tested pod restart.” Do not imply Kafka, RabbitMQ, Jenkins, Ansible or Kubernetes powered the application when they were only lab exercises.

## Freeze and continue

Retain the accepted V1 revision, demo instructions and evidence as a stable interview reference. Choose the next business capability through a small new scope decision, update the roadmap, and keep its results separate from the original V1 claims. See [target architecture](target-architecture.md) for candidate later work; it is not an instruction to implement every candidate.
