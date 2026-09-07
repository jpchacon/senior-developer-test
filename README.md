# Template Update Advisor

Part 2 of the take-home: one implemented slice of the system designed in **[DESIGN.md](DESIGN.md)**.
C4 diagrams are in **[DIAGRAMS.md](DIAGRAMS.md)**, and
**[AI-SESSION-HISTORY.md](AI-SESSION-HISTORY.md)** records how this was built with AI assistance —
including where the model was wrong and what caught it.

## What this slice is

The path from *"an engagement is behind"* to *"here is what changed, in language you can read"* —
chosen because it holds the two things hardest to get right in this design: the rule governing
accumulated and declined updates, and the boundary that keeps a language model from being trusted
with anything it should not be.

| Piece | Responsibility |
|---|---|
| `PendingUpdateResolver` | Is anything pending, and over what version range? |
| `PathBasedChangeClassifier` | **What** changed — deterministic, stable ids |
| `ChangeNarrator` | **How** to say it — the only seam where a model belongs |
| `SummaryValidator` | **Whether** that phrasing can be trusted |
| `DeterministicChangeRenderer` | What to show when it cannot |
| `ChangeSummaryService` | Composes the above; caches only validated output |
| `Jdbc*Repository` | PostgreSQL adapters — the only classes that touch the database |

Two rules are worth reading the tests for:

**Declining is deferral, not refusal.** On v3, decline v4, then v5 publishes → the pending range
is **v3→v5**, not v4→v5. The engagement never moved. `PendingUpdateResolverTest` pins this.

**A summary is checked, not believed.** Every sentence cites change-record ids, so three questions
are answered mechanically: does it cite a change that does not exist, does it omit one that does,
does it state a figure with no basis? `SummaryValidatorTest` feeds it deliberately hallucinated
output to prove the check bites.

## Running it

Requires JDK 21+, Maven, and Docker.

```bash
mvn verify                                  # all tests + coverage gate
mvn verify -DexcludedGroups=integration     # unit only, no Docker needed

docker compose up -d                        # local PostgreSQL on :5433
mvn spring-boot:run
```

Then:

```bash
curl 'http://localhost:8080/api/firms/firm-7/engagements/pending-updates?templateId=audit-ca&latestVersion=6'
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/projection.drift.detected
```

The pending-updates list is empty until the projection has rows — the projection is fed by events
from the engagement management system, which is outside this exercise. To see it working end to
end, insert a row directly:

```sql
INSERT INTO engagement_template_state (engagement_id, template_id, current_version, declined_versions)
VALUES ('eng-1', 'audit-ca', 3, '{4}');
```

Flyway migrates on startup. Testcontainers runs its own PostgreSQL on a random port, so a running
`docker compose` stack never collides with a test run.

## Tests

85 tests. Written test-first; the build gate is 95% line and branch.

```
LINE     411/419  98.1%
BRANCH   110/114  96.5%
```

**What is not covered, and why.** Four short-circuit branches whose second operand is unreachable
given the first; the `catch` blocks around JSON serialisation and SQL array construction, which
fire only on failures the schema and types already prevent; and `main`. Writing tests to force
those would mean mocking the JDK, which buys a number rather than confidence. Named here rather
than hidden behind an exclusion.

## Deliberately not built

The spec says this is not a full application build, and Spring Boot makes over-building cheap.

- **Apply/decline endpoints** — the engagement management system owns that decision.
- **Auth, pagination, a detail view** — surface area without an architectural argument.
- **Tenant routing** — `firmId` is accepted on the path but not yet used to select a schema; the
  slice runs against one. Multi-tenancy is schema-per-firm in the design.
- **A real LLM client** — `SummaryPipelineConfig` binds the narrator to the deterministic renderer
  by default, and refuses to start if told a model is enabled when none is wired. The system is
  fully functional with no model at all, which is the deployment order `DESIGN.md` argues for.
- **The projection worker, fan-out worker and backfill scheduler** — designed, not implemented.
  `findDistinctVersionsInUse` is here because it is the query that makes the bounded-precompute
  argument concrete rather than merely asserted.

`SampleTemplateDiffSource` stands in for the Product Template Store's diff facility, which the
brief places outside this exercise.

## A note on the diagrams

The C4 diagrams use Mermaid's native `C4Context`/`C4Container`/`C4Component` blocks, which are
still marked experimental upstream; Level 4 uses `classDiagram`, which is stable. **Rendering was
not verified locally** — no Mermaid renderer was available offline in this environment. If any
block renders poorly in your viewer, it should be converted to a `flowchart` keeping C4 discipline
(`Name [Type]` labels, `subgraph` boundaries, labelled relationships) rather than left broken.

## Postman collection

`postman/template-update-advisor.postman_collection.json` — 17 requests, 60 assertions, ordered
as a workflow. Import it with `postman/local.postman_environment.json`, or run it headless:

```bash
npx newman run postman/template-update-advisor.postman_collection.json \
    -e postman/local.postman_environment.json
```

| Folder | What it checks |
|---|---|
| 0. Preconditions | App up, database reachable, projection seeded — fails fast with an actionable message |
| 1. Core workflow | Accumulated backlog, single step, nothing pending, unknown template |
| 2. Domain invariants | Relationships that must hold for any data, including the decline rule |
| 3. Input validation | Every rejected input is a 4xx with a usable message |
| 4. Observability | Health plus the four metrics `DESIGN.md` commits to |

The invariant tests assert *relationships* rather than fixed values — `versionsBehind` equals the
range it describes, an update never runs backwards, and no declined version is ever at or below
`currentVersion`. That last one is the decline rule checked from outside the process: if declining
ever wrongly advanced the version a diff is computed from, it fails.

The collection needs the projection seeded first; the seed SQL is in the collection description,
since the projection is normally fed by events from the engagement management system.

## Lombok and builders

Lombok is present for exactly one annotation: **`@Builder`**. The rest of it (`@Getter`,
`@AllArgsConstructor`, `@EqualsAndHashCode`, `@ToString`) is redundant against records, which
already generate all of that.

Builders are applied where positional construction was a genuine hazard, not everywhere:

| Type | Why |
|---|---|
| `PendingUpdateView` | 8 components, 5 of them `String`/`int` |
| `ChangeRecord` | 3 adjacent `String` components |
| `JsonDiffEntry` | `before`/`after` — transposing them inverts a diff silently |
| `ChangeSet`, `PendingUpdate`, `ChangeSummaryKey` | `fromVersion`/`toVersion` share a type |
| `EngagementTemplateState` | 4 components including a collection |

Types with one or two components (`TemplateId`, `TemplateVersion`, `SummaryBullet`,
`NarratedSummary`) keep plain constructors — a builder there is ceremony with no benefit.

Three things worth knowing:

- **Builders do not bypass validation.** Lombok routes through the canonical constructor, so every
  compact-constructor check still fires. `BuilderContractTest` pins this, because "an invalid
  instance cannot exist" is load-bearing here and a builder would be a quiet way around it.
- **A builder gives up the compiler's completeness check.** A constructor forces you to pass every
  argument; a builder does not. Collection components therefore use `@Singular`, so an unset
  collection is empty rather than null. Scalars have no such safety net — this is the real cost of
  the pattern, and it is why builders were not applied to every type.
- **`lombok.config` sets `addLombokGeneratedAnnotation`**, so JaCoCo excludes generated builder
  code. Without it the coverage gate would be measuring code nobody wrote.

Lombok is `provided` scope *and* explicitly excluded from the repackaged jar — Boot's repackage
bundles provided-scope dependencies by default, which would ship ~2 MB of compile-time-only tooling
to production.

## Layout

```
src/main/java/com/caseware/templateupdate/
├── domain/        value types carrying their own invariants   (no Spring)
├── resolver/      the pending-update rule                     (no Spring)
├── summary/       classify → narrate → validate → fall back   (no Spring)
├── cache/         summary cache port, keyed by version pair   (no Spring)
├── persistence/   PostgreSQL adapters                         (Spring)
├── api/           one read endpoint                           (Spring)
└── config/        wiring and metrics                          (Spring)
```

Only the bottom three packages know Spring exists. The rules in the middle are plain Java,
constructed with `new` in their tests — which is why the unit suite runs in well under a second
and needs no container.
