> **Working document — not a submission artifact.**
> The deliverables are `DESIGN.md`, `DIAGRAMS.md`, and the Java slice.
> This file records the plan and the reasoning behind it.

# Plan: Caseware Senior SE Take-Home — Template Update Advisor

---

## Amendments after approval

Everything below the amendments was written and approved *before* these changes were requested.
It is left as it was rather than rewritten, so the record shows what was planned and what was
decided later.

### Amendment 1 — Lombok and the Builder pattern

**We are also implementing Lombok, applying the Builder pattern to the objects that take many
parameters to construct.** Lombok 1.18.46 — the version Spring Boot 4.1.0 manages, already in the
local repository, so the build stays offline.

*Scope, deliberately narrow.* This codebase is records throughout, so most of Lombok is redundant:
records already generate constructors, accessors, `equals`, `hashCode` and `toString`. Only
**`@Builder`** earns its place, and only where positional construction is a genuine hazard:

| Type | Why it gets a builder |
|---|---|
| `PendingUpdateView` | 8 components, 5 of them `String`/`int` |
| `ChangeRecord` | 3 adjacent `String` components |
| `JsonDiffEntry` | `before`/`after` — transposing them inverts a diff silently |
| `ChangeSet`, `PendingUpdate`, `ChangeSummaryKey` | `fromVersion`/`toVersion` share a type |
| `EngagementTemplateState` | 4 components including a collection |

Types with one or two components (`TemplateId`, `TemplateVersion`, `SummaryBullet`,
`NarratedSummary`, `JsonDiff`, `ValidationResult`) keep plain constructors. A builder there is
ceremony with no benefit, and the plan's bias toward small surface area still applies.

*The guardrail below is amended accordingly.* "No extra frameworks" now reads: no extra frameworks
**beyond Lombok**, and Lombok only for `@Builder`.

*What this had to preserve.* The design leans on "an invalid instance cannot exist". A builder that
skipped the compact constructor would quietly downgrade that to "cannot be constructed one
particular way". Lombok routes through the canonical constructor, so every validation still fires —
pinned by `BuilderContractTest` rather than assumed.

*What it costs.* A constructor forces every argument; a builder does not. Collection components use
`@Singular`, so an unset collection is empty rather than null — which also removes a latent
`List.copyOf(null)`. Scalars have no such safety net. That cost is the reason builders were not
applied to every type.

*Consequences that were not foreseen when this plan was written:*

- **A clean build broke, and incremental compilation hid it.** Lombok's generated builder members
  carry no JavaDoc, and doclint sees them after annotation processing; under `-Werror` that failed
  `mvn clean compile` while `mvn verify` still passed. Doclint is now scoped to
  `-Xdoclint:all/protected,-missing`. **This weakens a claim made below:** JavaDoc *completeness* is
  no longer enforced by the build, only JavaDoc *correctness* — a broken `@param`, an unresolvable
  `{@link}`, malformed HTML still fail (verified deliberately). The Testing Strategy and
  Verification sections below should be read with that correction.
- **`lombok.config` sets `addLombokGeneratedAnnotation`**, so JaCoCo excludes generated code.
  Without it the coverage gate would measure code nobody wrote.
- **Lombok is excluded from the repackaged jar.** `provided` scope was not enough: Boot's repackage
  bundles provided-scope dependencies, so ~2 MB of a compile-time-only tool was shipping in
  `BOOT-INF/lib`.
- **Java 24 needs explicit `annotationProcessorPaths`** — JDK 23+ no longer discovers processors on
  the classpath implicitly.

### Amendment 2 — Postman collection, and a defect it exposed

Added on request: `postman/template-update-advisor.postman_collection.json` (17 requests, 60
assertions, verified with Newman) plus a local environment file.

Writing the validations surfaced a real bug that no unit test had: invalid input — `latestVersion=0`,
a negative version, a blank `templateId` — returned **500**. The domain types throw
`IllegalArgumentException` and nothing mapped it, so a caller's mistake reported as a server fault
and would have inflated the error-rate signal that this design's alerting depends on.

Fixed with a one-class `ApiExceptionHandler` mapping those to **400 `application/problem+json`**,
rather than writing a test that enshrined the defect. Also documented a limitation the tests
exposed: `firmId` is accepted on the path but not yet used to select a tenant.

---

## Context

The Caseware take-home brief (kept outside this repository) sets an
architecture exercise. Customers work in **Engagement Files** created from versioned
**Product Templates**. Templates are updated ~weekly; for each engagement the firm must
**apply or decline** each update. We must design a system that shows, at a glance, which
of a firm's ~100s of engagements have pending updates, plus a **human-readable** summary
of the inbound changes to support that decision. Applying the update is out of scope.

**The binding constraint:** an engagement's current template version is not queryable —
reading it requires loading the engagement (~1 min, stated as hard). 100s of engagements
per firm makes any polling design impossible. This single fact dictates the architecture.

Deliverables per the spec: a **1–3 page** design doc covering five named sections,
optional code slice, and any diagrams. Time budget 2–3 hours; the spec explicitly says
*"do not over-optimize"* and *"optimize for clarity and tradeoffs over completeness."*
Length discipline is itself being graded under "Clarity of communication."

**Decisions already made with the user:** design doc + one code slice; slice =
pending-update resolver + grounded summarizer; **Java on Spring Boot 4.1.0, built with
Maven** (every artifact verified cached offline); diagrams as **C4 (all four levels)** in
Mermaid, delivered in a separate `DIAGRAMS.md` with Level 2 embedded in the design doc;
**PostgreSQL** as the store for both the change summaries and the engagement registry, run
locally as a Docker `postgres:16` container and tested via Testcontainers; **TDD** with a
JaCoCo coverage gate; Clean Code, SOLID and enforced JavaDoc throughout.

---

## The three insights the design rests on

1. **Project, don't read.** The engagement management system is the *sole writer* for all
   three transitions that change an engagement's template version — create, apply,
   decline. Hook those three and the projection is exact, not approximate. The dashboard
   then reads a millisecond-latency table instead of loading engagements.

2. **The diff is firm-independent.** v3→v4 of a template is byte-identical for every firm.
   Summarize **once per version-pair**, key by `(templateId, fromVersion, toVersion)`,
   cache forever (versions are immutable). This turns hundreds of thousands of LLM calls
   into one per week per product — and means a single content-team SME review covers every
   customer. It is safe to share cross-firm precisely because the summary derives only
   from template content and never touches engagement data (stated as an invariant).

3. **Accumulation needs the net diff, not composed steps.** Engagement on v3 with v4/v5/v6
   published: composing three step-summaries contradicts itself (a field added in v4 and
   removed in v6 appears as both). The user decides on the *net* change, so compute the
   direct v3→v6 diff. The N² blowup is bounded because the projection tells us which
   `from` versions are actually in use — a handful, not the full matrix.

**Assumption to state explicitly in the doc:** *decline = "not now" for that version, not
"never."* If a user declines v4 and v5 then publishes, the engagement is still on v3, so
the correct diff is **v3→v5**. Declined versions are recorded for UI context but never
advance the `from` pointer. This is the subtlest rule in the problem and the resolver
encodes it directly.

---

## Deliverable 1 — `DESIGN.md` (the graded artifact)

Hard cap 1–3 pages (~900–1400 words + the one embedded C4 Level 2 diagram). Sections, in
the spec's own order:

**Assumptions** (short bullet list, incl. the decline rule and immutable versions).

**1. High-Level Architecture** — two decoupled planes, one **PostgreSQL** cluster:
- *Global plane (firm-independent):* `TemplatePublished` hook → compute JSON diff vs prior
  version → deterministic change classifier → LLM narration → validator → **Change Summary
  Store**: table `template_change.change_summary`, primary key
  `(template_id, from_version, to_version)`. Immutable, shared across all firms, contains
  no customer data. Classified change records land in a `jsonb` column — a natural fit,
  since templates are JSON to begin with, and it keeps the records queryable.
- *Tenant plane (per-firm):* **Engagement Template Registry**, table
  `firm_<id>.engagement_template_state`, a projection of `EngagementCreated` /
  `TemplateUpdateApplied` / `TemplateUpdateDeclined`, plus a free `EngagementOpened`
  reconciliation heartbeat. PK `engagement_id`; index on `(template_id, current_version)`
  to drive fan-out.
- *Tenancy model:* **schema-per-firm** for tenant data plus one shared `template_change`
  schema, all in one cluster, isolation enforced by schema grants (Row-Level Security if
  firms are ever co-located in one schema). This mirrors the per-customer database topology
  the spec already describes, and leaves an escape hatch: a firm needing physical isolation
  moves to its own database and only the repository implementation changes.
- *Fan-out on publish:* `SELECT DISTINCT current_version FROM engagement_template_state
  WHERE template_id = ?` gives exactly the `from` versions in use → enqueue precompute for
  those `(from → latest)` pairs. Demand-driven and bounded — and in SQL this is one plain
  indexed query rather than a secondary-index workaround.
- *Read path:* the badge list and the detail view are **a single join** between the tenant's
  `engagement_template_state` and the shared `change_summary`, because co-locating the two
  schemas in one cluster makes the join possible. This is the concrete payoff of choosing a
  relational store, and worth naming as such in the doc.
- AWS shape: EventBridge → SQS → ECS/Lambda workers; **RDS/Aurora PostgreSQL** for both
  schemas; S3 for raw diff blobs too large to keep inline. Flyway owns schema migrations.

Level 2 (Container) is embedded here inline; the full C4 set lives in `DIAGRAMS.md` — see
**Deliverable 2**.

**2. Implementation Plan** — phased, each phase independently shippable:
- P0: event contracts + transactional outbox in the engagement system; Flyway baseline for
  the `template_change` schema and the per-firm schema template.
- P1: registry projection + **backfill** (lazy-on-natural-open, plus a rate-budgeted
  background sweep that never competes with interactive load; unknown state rendered
  honestly as "checking…").
- P2: diff + deterministic classifier + **structured rendering — ship before any LLM.**
  The product is useful with a plain categorized list; AI is an enhancement, not a
  dependency. (Deliberate maturity signal.)
- P3: LLM narration + validator + SME review queue.
- P4: dashboard read API and badges.

**3. Testing Strategy**
- **TDD is the stated working practice, not an aspiration.** Every unit of the slice is
  written test-first: red → green → refactor. The domain here is rule-heavy and almost
  entirely pure functions (resolver, classifier, validator), which is the case where TDD
  pays for itself — the rules (especially the decline rule) are easier to state as
  executable examples than as prose, and the tests become the specification.
- **Coverage target: as close to 100% as possible**, enforced by a JaCoCo gate in the
  build rather than tracked by convention. Because the core is pure logic with the LLM and
  storage pushed behind interfaces, near-total coverage is genuinely reachable here — the
  usual excuse (untestable I/O) has been designed out. Anything left uncovered must be
  named and justified, not silently tolerated.
- Unit/table-driven: resolver rules (accumulation, decline, no-op, out-of-order, idempotency).
- Contract tests on the three event schemas (producer/consumer).
- Projection replay: duplicate, reordered, and dropped events → assert convergence.
- Summarizer: golden diff corpus; **adversarial stub LLM output** injected to prove the
  validator catches hallucination.
- Integration: publish → fan-out → summary available, against a fake template DB.
- **Persistence tested against real PostgreSQL, not an in-memory substitute.** Testcontainers
  spins up `postgres:16` per run, Flyway applies the real migrations, and the repository
  tests exercise actual SQL. H2 is cached and available but deliberately rejected: it does
  not faithfully reproduce `jsonb`, upsert semantics or RLS, so passing against H2 would
  prove nothing about production. Migrations are tested by being run, every time.
- **No live LLM in CI** — stubbed behind an interface; the real model runs in a separate
  scheduled eval job. Postgres, by contrast, *is* real in CI, because it is deterministic
  and cheap to stand up while an LLM is neither.

**4. Evaluation & Observability** — split deliberately into two:
- *System:* event lag / registry staleness; **projection drift counter** from
  reconciliation-on-open (this is the key SLI — it directly measures whether the
  projection premise holds); summary cache hit rate; precompute p99; backfill budget
  consumption; fan-out queue depth.
- *AI:* SME-reviewed golden set. **Coverage** (every change record referenced) and
  **fidelity** (no claim absent from the diff) are mechanically checkable via the
  traceability links, not vibes. LLM-as-judge gates prompt/model changes, with human
  spot-checks. Real-world signal: SME **edit rate** on generated summaries, plus user
  decision latency and apply-reversal rate.

**5. Failure Modes & Tradeoffs**
- Dropped event → drift → **self-heals on natural engagement open** (the system already
  has the true version in memory at that moment; emitting it costs nothing) + low-rate
  audit sweep. Bounded staleness rather than guaranteed consistency.
- LLM down or output fails validation → deterministic structured list. Degrade, never block.
- Backfill vs. interactive contention → hard rate budget against the 1-min constraint.
- Publish storm → async queue with a "summary preparing" UI state.
- Eventual consistency: badge may lag minutes — fine for weekly updates.
- Postgres-specific: connection-pool exhaustion under fan-out (bounded pool + queue
  backpressure rather than unbounded workers); a failed migration blocking deploy (Flyway
  runs forward-only, migrations are tested by execution in CI); the shared `change_summary`
  table being a cross-tenant read path (read-only grant for tenant roles, and the
  no-customer-data invariant asserted by a test, not just asserted in prose).
- **Tradeoff — PostgreSQL over a key-value store:** chosen for relational leverage the
  workload genuinely needs — `SELECT DISTINCT` for fan-out, and a single join for the read
  path instead of an application-level join across two stores — plus schema migrations,
  transactions and ordinary operational familiarity. The cost is that writes are bounded by
  a single primary and tenancy has to be modelled deliberately rather than falling out of a
  partition key. At ~weekly publishes and ~100s of engagements per firm, that ceiling is
  nowhere near binding, so the relational win is free. It would be the wrong call at a
  volume this problem does not have.
- Other tradeoffs stated as chosen-with-reason: net diff over composed steps (correctness in
  an audit domain); projection over loading engagements (the latter is simply impossible);
  shared summaries (cost + one SME review for all) guarded by the no-customer-data invariant.

**AI Usage** (short section — they ask about it at review): where AI helped, where it was
overridden, and **where it must not be trusted here** — the LLM never determines *whether*
an update exists (registry, deterministic) nor *what* changed (JSON diff, deterministic);
it only narrates a pre-classified change set with per-sentence traceability, and it never
recommends apply/decline, because that is professional judgment the spec says AI must augment
rather than replace.

---

## Deliverable 2 — `DIAGRAMS.md`: the four C4 levels

**Where they live.** Four C4 diagrams will not fit inside a 1–3 page design doc, and the
page limit is itself graded. The spec resolves this for us: *"Any diagrams you created"* is
listed as **deliverable #4, separate from the design document**. So all four levels live in
`DIAGRAMS.md`, which has no page limit, and `DESIGN.md` embeds only **Level 2 (Container)** —
the one level that carries the architectural argument — and links to the rest. No diagram is
duplicated across the two files.

**Notation.** Mermaid's native `C4Context` / `C4Container` / `C4Component` blocks, using
proper C4 element types (`Person`, `System`, `System_Ext`, `Container`, `ContainerDb`,
`Component`) and `Boundary` groupings, with a legend. *Risk:* Mermaid's C4 support is still
flagged experimental and some renderers (including GitHub at times) handle it poorly. If it
does not render cleanly, fall back to `flowchart` diagrams that keep C4 discipline — every
node labelled `Name [Type]` plus a one-line description, `subgraph` for boundaries, labelled
directed relationships — rather than shipping a diagram that only renders on my machine.
Level 4 uses `classDiagram`, which is stable and is the conventional choice for that level.

**Level 1 — System Context.** Who uses this and what it touches.
Persons: *Audit Practitioner* (sees pending updates, decides apply/decline) and *Content
Author* (publishes template updates, and — per the design — reviews generated summaries).
Systems: **Template Update Advisor** (the system being designed) against four externals —
*Product Template Store* (shared across firms), *Engagement Management System* (~1 min
loads; owns apply/decline), *Engagement File Store* (per-firm), and an *LLM Service*.
The one thing this level must make visible is the trust/tenancy split: the Advisor straddles
a firm-independent zone and a per-firm zone, which is the whole basis of insight 2.

**Level 2 — Container.** Inside the Advisor, split by boundary:
*Global (firm-independent):* `Change Analysis Worker` [Spring Boot, ECS/Lambda], `Publish
Fan-out Worker` [Spring Boot], and `Change Summary Store` [**ContainerDb: PostgreSQL** — schema `template_change`,
`jsonb` change records, no customer data].
*Per-tenant:* `Engagement Projection Worker` [Spring Boot], `Engagement Template Registry`
[**ContainerDb: PostgreSQL** — schema `firm_<id>`, indexed on `(template_id,
current_version)`], `Backfill Scheduler` [rate-budgeted], `Update Advisor API` [Spring Boot, REST + Actuator].
Plus the `Event Bus` [EventBridge] and `Queues` [SQS] carrying `TemplatePublished` and the
engagement lifecycle events.
Draw both schemas inside a single `PostgreSQL cluster (RDS/Aurora)` boundary — that grouping
is what makes the single-join read path legible at a glance, and it is the detail a reviewer
will look for. Note on the diagram that local development runs the same cluster as a Docker
`postgres:16` container. This is the diagram embedded in `DESIGN.md`.

**Level 3 — Component.** Two diagrams, one per significant container:
1. *Change Analysis Worker* — `TemplatePublishedConsumer`, `TemplateStoreClient`,
   `ChangeClassifier`, `ChangeNarrator` (LLM adapter), `SummaryValidator`,
   `DeterministicChangeRenderer`, `ChangeSummaryService`, and
   `JdbcChangeSummaryRepository` drawn with its relationship to the PostgreSQL container.
2. *Update Advisor API* — `PendingUpdateResolver`, `JdbcEngagementStateRepository`,
   `JdbcChangeSummaryRepository`, and the badge-list / detail-view endpoints.
Both diagrams should show the JDBC adapters as the only components touching the database,
which is the visual form of the ports-and-adapters split the code uses.
The resolver is drawn in the API container and shared with the fan-out worker as a domain
library, which is exactly how the code is laid out.

**Level 4 — Code.** A `classDiagram` of the Part 2 slice: the domain records, the four
interfaces (`ChangeClassifier`, `ChangeNarrator`, `SummaryRenderer`, `SummaryCache`),
`SummaryValidator`, `PendingUpdateResolver` and `ChangeSummaryService`, with the dependency
arrows pointing at abstractions to make DIP visible. C4 normally treats level 4 as optional
and rarely worth maintaining — it earns its place here only because it diagrams code that
actually exists in this submission rather than code that might one day be written, and it is
the bridge between the design doc and Deliverable 3. Worth saying that out loud in the file.

---

## Deliverable 3 — Java slice: `template-update-advisor`

**Spring Boot 4.1.0 + Maven**, `maven.compiler.release=21`, package
`com.caseware.templateupdate`, parent `spring-boot-starter-parent:4.1.0`.

**Why 4.1.0 specifically:** the cache holds Boot 3.1.1, 3.5.7 and 4.1.0, but 4.1.0 is the
only one where *all* the pieces this design needs are present — `spring-boot-starter-actuator`,
`spring-boot-starter-flyway` and `spring-boot-testcontainers` are cached at 4.1.0 and at no
earlier version. Boot 4 also renames `starter-web` to `starter-webmvc`, which matches what is
cached, so the layout is internally consistent. Java 24 is active and Boot 4 needs 17+.

| Dependency (Boot BOM-managed unless noted) | Scope |
|---|---|
| `spring-boot-starter-webmvc` | compile |
| `spring-boot-starter-jdbc` (`JdbcClient`) | compile |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` 12.4.0 | compile |
| `spring-boot-starter-validation` | compile |
| `spring-boot-starter-actuator` + Micrometer 1.17.0 | compile |
| `postgresql` JDBC 42.7.11 | runtime |
| `spring-boot-starter-test` (JUnit 5.12.2, AssertJ 3.27.7, Mockito) | test |
| `spring-boot-testcontainers` 4.1.0 + Testcontainers `postgresql` 1.20.4 | test |
| `spring-boot-maven-plugin` 4.1.0 · JaCoCo 0.8.13 | build |

`postgres:16` is already pulled locally and every artifact above is in `~/.m2`, so the build
and its container tests should run offline. **Verify that assumption first** with a single
`mvn -o -q test-compile` before writing much code — a Boot parent drags in a large transitive
tree and one missing jar is cheaper to discover in minute one than in hour two. Documented
fallback if something is absent: Boot 3.5.7 with `starter-web`, plus standalone `flyway-core`
and no actuator.

Jackson arrives via the web starter, so `jsonb` columns are mapped through it rather than by
hand — but the diff boundary stays plain records regardless.

**Built test-first.** For each unit below, the test class is written before the
implementation and the failing test is observed before code is added. The order is chosen
so the rules drive the design: resolver → validator → classifier → cache/fallback wiring.
JaCoCo runs in the `verify` phase with an enforced minimum (start at 95% line **and**
branch, raise toward 100% as the slice settles; the build fails below it). Coverage is a
floor here, not the goal — the tests below are chosen because each one pins a claim made
in the design doc.

### Domain (`domain/`) — immutable value types that carry their own invariants

All records validate in a compact constructor (fail fast, meaningful message) and defensively
copy collections, so an invalid or mutable instance cannot exist.

```java
public record TemplateId(String value) { }
public record EngagementId(String value) { }

public record TemplateVersion(int number) implements Comparable<TemplateVersion> {
    public boolean isAfter(TemplateVersion other);
    public boolean isBefore(TemplateVersion other);
}

/** The projected state of one engagement. Monotonic: it never moves backwards. */
public record EngagementTemplateState(EngagementId engagementId,
                                      TemplateId templateId,
                                      TemplateVersion currentVersion,
                                      Set<TemplateVersion> declinedVersions) {
    public boolean hasDeclined(TemplateVersion version);
    public EngagementTemplateState withApplied(TemplateVersion version);   // ignores non-advancing
    public EngagementTemplateState withDeclined(TemplateVersion version);
}

public enum ChangeKind { ADDED, REMOVED, MODIFIED }

public record ChangeRecord(String id, ChangeKind kind, String area,
                           String humanPath, String detail) { }

public record ChangeSet(TemplateId templateId, TemplateVersion fromVersion,
                        TemplateVersion toVersion, List<ChangeRecord> records) {
    public boolean isEmpty();
    public Set<String> recordIds();
    public Optional<ChangeRecord> findById(String id);
}

public record PendingUpdate(EngagementId engagementId, TemplateId templateId,
                            TemplateVersion fromVersion, TemplateVersion toVersion,
                            List<TemplateVersion> declinedInRange) {
    public int versionsBehind();
}

public record SummaryBullet(String text, List<String> citedChangeIds) { }
public record NarratedSummary(String headline, List<SummaryBullet> bullets) { }
```

`withApplied`'s monotonic guard is where idempotency and out-of-order event delivery are
handled — it belongs on the state because it is an invariant of the state, not of a caller.

The JSON diff boundary is modelled as plain records so the slice needs **no JSON library**:

```java
public record JsonDiffEntry(String pointer, ChangeKind kind, String before, String after) { }
public record JsonDiff(List<JsonDiffEntry> entries) { }
```

### Resolver (`resolver/`)

```java
public final class PendingUpdateResolver {
    public Optional<PendingUpdate> resolve(EngagementTemplateState state,
                                           TemplateVersion latestPublished);
}
```

A pure function, no dependencies. Three rules, each stated in JavaDoc with a worked example:
`latestPublished` not after `currentVersion` → empty; otherwise `from = currentVersion` and
`to = latestPublished`; declined versions in that range are reported for UI context but
**never advance `from`**.

### Summary pipeline (`summary/`)

```java
public interface ChangeClassifier {                    // deterministic: what changed
    ChangeSet classify(TemplateId id, TemplateVersion from, TemplateVersion to, JsonDiff diff);
}
public interface ChangeNarrator {                      // the LLM seam: how to say it
    NarratedSummary narrate(ChangeSet changeSet) throws NarrationException;
}
public interface SummaryRenderer {                     // the always-available fallback
    NarratedSummary render(ChangeSet changeSet);
}
public final class SummaryValidator {                  // whether to trust it
    public ValidationResult validate(ChangeSet changeSet, NarratedSummary summary);
}
public record ValidationResult(boolean valid, List<String> violations) {
    public static ValidationResult ok();
    public static ValidationResult failed(List<String> violations);
}
```

Implementations: `PathBasedChangeClassifier` (stable IDs derived from the JSON pointer, so
the same diff always yields the same IDs), `DeterministicChangeRenderer` (the fallback,
groups records by area), and `LlmChangeNarrator` (sketched, schema-constrained output).

`SummaryValidator` is the centerpiece and its three rules are three named private methods
so `validate` reads as the rules themselves: `findDanglingCitations` (a bullet cites an ID
not in the change set), `findUncoveredRecords` (a change no bullet mentions), and
`findUnsupportedNumbers` (a numeric literal in the prose that appears nowhere in the change
set). Adding a fourth rule means adding a method, not editing the others.

### Cache and orchestration (`cache/`, root)

```java
public record ChangeSummaryKey(TemplateId templateId,
                               TemplateVersion fromVersion, TemplateVersion toVersion) { }
public interface SummaryCache {
    Optional<NarratedSummary> get(ChangeSummaryKey key);
    void put(ChangeSummaryKey key, NarratedSummary summary);
}

public final class ChangeSummaryService {
    public ChangeSummaryService(ChangeNarrator narrator, SummaryValidator validator,
                                SummaryRenderer fallbackRenderer, SummaryCache cache);
    public NarratedSummary summarize(ChangeSet changeSet);
}
```

The key has no firm or engagement in it — that *is* insight 2, expressed as a type.
`summarize` reads: cache hit returns; otherwise narrate, validate, and fall back to the
deterministic renderer if narration throws or fails validation. **Only validated narration
is cached**, so a transient LLM outage cannot poison the cache permanently — recorded as an
`@implNote`, since it is the kind of decision a future reader would otherwise "simplify" away.

### Persistence (`persistence/`) and schema

The ports were already there — adding Postgres means writing adapters, not restructuring.
`SummaryCache` gains a sibling port and both get JDBC implementations:

```java
public interface EngagementStateRepository {
    Optional<EngagementTemplateState> findById(EngagementId id);
    void save(EngagementTemplateState state);                        // idempotent upsert
    List<TemplateVersion> findDistinctVersionsInUse(TemplateId templateId);
}

@Repository
public class JdbcEngagementStateRepository implements EngagementStateRepository {
    JdbcEngagementStateRepository(JdbcClient jdbcClient) { ... }   // constructor injection
}
@Repository
public class JdbcChangeSummaryRepository implements SummaryCache { }
```

Spring's `JdbcClient` rather than an ORM: there are perhaps six queries in the whole slice,
each expressing a design rule (the monotonic upsert, the distinct-versions fan-out), and they
are clearer read as SQL than reconstructed from JPA annotations. Flyway runs on startup via
the starter, so a fresh `docker compose up` yields a migrated database with no extra step.

`findDistinctVersionsInUse` is the fan-out query from insight 3, expressed as one line of
SQL — and having it on the port makes the bounded-precompute argument executable rather than
merely asserted.

Flyway migrations in `src/main/resources/db/migration/`:

```sql
-- V1__template_change_schema.sql   (global, firm-independent)
CREATE SCHEMA template_change;
CREATE TABLE template_change.change_summary (
    template_id   text    NOT NULL,
    from_version  integer NOT NULL,
    to_version    integer NOT NULL,
    headline      text    NOT NULL,
    bullets       jsonb   NOT NULL,
    change_records jsonb  NOT NULL,
    source        text    NOT NULL,   -- 'LLM_VALIDATED' | 'DETERMINISTIC_FALLBACK'
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT change_summary_pk PRIMARY KEY (template_id, from_version, to_version),
    CONSTRAINT change_summary_version_order CHECK (to_version > from_version)
);

-- V2__firm_schema.sql              (per-tenant)
CREATE TABLE engagement_template_state (
    engagement_id     text    NOT NULL PRIMARY KEY,
    template_id       text    NOT NULL,
    current_version   integer NOT NULL,
    declined_versions integer[] NOT NULL DEFAULT '{}',
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX engagement_template_state_fanout
    ON engagement_template_state (template_id, current_version);
```

Two constraints carry design intent into the database, which is the point of using one:
`change_summary_version_order` makes a backwards range unrepresentable, and the composite
primary key is the firm-independence invariant — there is no column in which a `firm_id`
could be stored even by mistake. `source` records whether a row came from validated LLM
narration or the deterministic fallback, so the cache-only-validated rule is auditable after
the fact, and fallback rows can be swept and recomputed once the LLM recovers.

Upserts use `INSERT ... ON CONFLICT DO UPDATE` with a monotonic guard
(`WHERE excluded.current_version > engagement_template_state.current_version`), which is the
same idempotency rule as `withApplied`, enforced a second time at the only place concurrent
event consumers actually race.

### API surface (`api/`) — deliberately one endpoint

```java
@RestController
class PendingUpdateController {
    @GetMapping("/api/firms/{firmId}/engagements/pending-updates")
    List<PendingUpdateView> listPendingUpdates(@PathVariable String firmId);
}
```

One read endpoint: the badge list, joined to summaries, which is the user-facing question the
whole design exists to answer. **Not** in scope: apply/decline endpoints (the spec puts
applying out of scope, and decisions belong to the engagement system), auth, pagination,
a second endpoint for the detail view. The spec says *"this is not a full application build"*,
and Spring Boot makes it very easy to drift into building one — so the restraint is recorded
here rather than left to in-the-moment judgment.

`PendingUpdateView` is a separate response record, not the domain `PendingUpdate` — the wire
format and the domain model are allowed to diverge later without one dragging the other along.

### Observability (`config/`) — Actuator makes section 4 real

The Evaluation & Observability section stops being aspirational once Actuator and Micrometer
are on the classpath. Wire the specific meters the design doc names:
`projection.drift.detected` (the key SLI from reconciliation-on-open),
`summary.cache.hit` / `summary.cache.miss` (the shared-cache cost argument),
`narration.validation.failed` (how often the LLM is caught), and
`narration.fallback.used`. Health at `/actuator/health` includes the Postgres check for free.

Being able to point at a running `/actuator/metrics/narration.validation.failed` during the
live design review is a stronger answer than a paragraph claiming the metric would exist.

### Local development — `docker-compose.yml`

A single `postgres:16` service (image already local), fixed port, named volume, healthcheck.
`README.md` documents `docker compose up -d` then `mvn verify`. Testcontainers manages its
own container for tests, so compose is purely for manual exploration — the two do not share
a port, and that is worth stating so nobody debugs a phantom conflict.

### SOLID, concretely

- **SRP** — five reasons to change, five types: the resolver decides *whether and over what
  range*, the classifier *what changed*, the narrator *how to phrase it*, the validator
  *whether to trust it*, the service *how they compose*.
- **OCP** — a new narrator (different model or vendor), a new change area, or a fourth
  validation rule plugs in without modifying existing classes.
- **LSP** — every `ChangeNarrator` honours one contract: return a `NarratedSummary` or throw
  `NarrationException`. The stub, the deliberately-failing stub and the real client are
  substitutable, which is exactly what makes the fallback test meaningful rather than staged.
- **ISP** — `ChangeNarrator`, `SummaryRenderer` and `SummaryCache` stay three small
  interfaces instead of one `SummaryPort`; the deterministic renderer never has to pretend
  it can cache.
- **DIP** — `ChangeSummaryService` depends only on interfaces, constructor-injected; no LLM
  client is constructed anywhere in the core. This is also *why* near-100% coverage is
  reachable offline, so the SOLID argument and the coverage target are the same decision.

Spring Boot fits this without bending it: the design already used constructor injection and
interface-typed collaborators, so the container simply supplies what was being passed by hand.
Keep it that way — **no field injection and no `@Autowired`**, so every class stays
constructible in a plain unit test with `new` and the Spring context is needed only for the
wiring and persistence tests. The domain, resolver, validator and renderer packages stay
entirely free of Spring annotations; only `persistence/`, `api/` and `config/` know Spring
exists. That boundary is what keeps the core testable in milliseconds.

### Clean Code conventions applied

Intention-revealing names with no abbreviations (`declinedInRange`, never `dvr`); small
functions at one level of abstraction; guard clauses instead of nested conditionals; no
boolean parameters; no `null` returns — `Optional` at boundaries and empty collections
otherwise; immutability everywhere via records, `List.copyOf` and defensive copies.
Comments explain *why* (the decline rule, cache-only-validated) and never restate *what*.
Tests are named as behaviour sentences (`declineDoesNotAdvanceTheFromVersion`) with
`@DisplayName`, arranged Arrange–Act–Assert, and share a small fixture builder so each test
reads as one sentence about the domain.

### JavaDoc standards

Every public type and public method is documented. Type-level JavaDoc states the
responsibility in a single sentence plus the invariant the type protects, and cross-references
the relevant `DESIGN.md` section so code and doc stay tied together. Method JavaDoc expresses
`@return` in domain terms rather than mechanics, with `@param` and `@throws` complete.
`PendingUpdateResolver#resolve` carries the worked decline example (on v3, decline v4, v5
publishes → the range is **v3→v5**) in a `<pre>` block, because that rule is the one a reader
is most likely to get wrong. Record components are documented via `@param` on the record
header. Each package gets a `package-info.java` naming which plane it belongs to — global
(firm-independent) or tenant — so the code layout mirrors the architecture diagram.

### Tests (`src/test/java/...`) — each pins a claim made in the doc

- `PendingUpdateResolverTest` — `@Nested` groups: NoPendingUpdate, SingleStep, Accumulation
  (v3→v6), Declines (**decline v4, then v5 publishes → range stays v3→v5**; decline of the
  latest → nothing pending until the next publish).
- `EngagementTemplateStateTest` — monotonic advance, idempotent re-delivery, out-of-order
  events, defensive copy of `declinedVersions`.
- `SummaryValidatorTest` — dangling citation rejected; uncovered change record rejected;
  invented number rejected; well-formed summary accepted; violations accumulate.
- `ChangeSummaryServiceTest` — narrator throws → fallback; narration fails validation →
  fallback; **two engagements sharing a key → exactly one narration call** (asserted via an
  invocation-counting stub, proving the cost argument in executable form); fallback output
  is not cached.
- `PathBasedChangeClassifierTest` — grouping by area; identical diffs produce identical IDs.
- `DeterministicChangeRendererTest` — every `ChangeKind`, and the empty change set.
- **`JdbcEngagementStateRepositoryIT`** and **`JdbcChangeSummaryRepositoryIT`** — against a
  real `postgres:16` via Testcontainers, with Flyway applying the actual migrations. Use Boot's
  `@ServiceConnection` on a `@Container` static field, which wires the datasource with no
  properties file at all, and share the container across the suite so it stays quick. Cases:
  round-trip with `declined_versions` preserved; upsert is idempotent and never moves
  `current_version` backwards even when events replay out of order;
  `findDistinctVersionsInUse` returns exactly the in-use set; the
  `change_summary_version_order` constraint rejects an inverted range; `jsonb` bullets
  survive the round trip intact.
- **`PendingUpdateControllerTest`** — `@WebMvcTest` with the service mocked: asserts the JSON
  contract of the badge list, no database involved.
- **`ApplicationContextIT`** — a single `@SpringBootTest` that the context loads and Flyway
  migrates. Cheap, and it catches the wiring mistakes that unit tests structurally cannot.

Integration tests are tagged `@Tag("integration")`, so `mvn verify -DexcludedGroups=integration`
still gives a pure-unit run with no Docker. The JaCoCo gate assumes the full run — state
plainly in the README that the JDBC adapters are covered by the container-backed tests and
that skipping them will drop coverage below the threshold, rather than letting someone
discover that as a confusing build failure.

**`README.md`**: what the slice is, what it deliberately omits, how to run, and any
deliberately uncovered branch named explicitly.

---

## Files to create

**Step 0 — save this plan into the project first.** Copy this file to
`/Users/juanchacon/Documents/WorkspaceCaseware/PLAN.md` before any other work, so the plan
lives alongside the deliverables and survives the session. It is a working document, not a
submission artifact — note that at the top of it, so a reviewer reading the folder is not
confused about which files are the actual deliverables.

```
PLAN.md                                    ← this plan (working doc, not submitted)
DESIGN.md                                  ← the graded deliverable (embeds C4 L2 only)
DIAGRAMS.md                                ← C4 levels 1-4
README.md
pom.xml                                    ← java 21, junit 5.12.2, surefire, jacoco gate
src/main/java/com/caseware/templateupdate/
├── ChangeSummaryService.java              ← orchestrator (DIP: interfaces only)
├── package-info.java
├── domain/        TemplateId, TemplateVersion, EngagementId, EngagementTemplateState,
│                  ChangeKind, ChangeRecord, ChangeSet, PendingUpdate, SummaryBullet,
│                  NarratedSummary, JsonDiff, JsonDiffEntry, package-info
├── resolver/      PendingUpdateResolver, package-info
├── summary/       ChangeClassifier, PathBasedChangeClassifier, ChangeNarrator,
│                  NarrationException, LlmChangeNarrator, SummaryRenderer,
│                  DeterministicChangeRenderer, SummaryValidator, ValidationResult,
│                  package-info
├── cache/         ChangeSummaryKey, SummaryCache, InMemorySummaryCache, package-info
├── persistence/   EngagementStateRepository, JdbcEngagementStateRepository,
│                  JdbcChangeSummaryRepository, package-info     ← Spring lives here
├── api/           PendingUpdateController, PendingUpdateView, package-info
├── config/        SummaryPipelineConfig (@Bean wiring), MetricsConfig, package-info
└── TemplateUpdateAdvisorApplication.java   ← @SpringBootApplication
src/main/resources/
├── application.yml                        ← datasource, flyway, actuator exposure
└── db/migration/
    ├── V1__template_change_schema.sql     ← global schema
    └── V2__firm_schema.sql                ← per-tenant schema
docker-compose.yml                         ← postgres:16 for local exploration
src/test/java/com/caseware/templateupdate/
├── resolver/PendingUpdateResolverTest.java
├── domain/EngagementTemplateStateTest.java
├── summary/SummaryValidatorTest.java, PathBasedChangeClassifierTest.java,
│           DeterministicChangeRendererTest.java
├── ChangeSummaryServiceTest.java
├── persistence/JdbcEngagementStateRepositoryIT.java,
│               JdbcChangeSummaryRepositoryIT.java   ← Testcontainers + @ServiceConnection
├── api/PendingUpdateControllerTest.java             ← @WebMvcTest
├── ApplicationContextIT.java                        ← @SpringBootTest smoke
└── testsupport/  ChangeSetFixtures, CountingChangeNarrator, FailingChangeNarrator,
                  PostgresSupport (shared container definition)
```

The take-home brief itself is deliberately not committed — it is Caseware's document, not mine
to publish.

## Verification

- **First:** `mvn -o -q test-compile` on a skeleton pom, to prove the Spring Boot 4.1.0
  dependency tree resolves entirely from `~/.m2`. If it does not, drop to the documented
  Boot 3.5.7 fallback before writing code rather than after.
- Confirm the Docker daemon is up (verified running: 28.3.3, with `postgres:16` already
  pulled — no image download needed).
- `mvn -q verify` → all unit **and** Testcontainers tests green, Flyway migrations applied
  against real PostgreSQL, and the JaCoCo gate passing. Every dependency version in the
  table above was confirmed present in `~/.m2`, so this runs with no network.
- `mvn -q verify -DexcludedGroups=integration` → still green, proving the unit core stands
  alone without Docker.
- `docker compose up -d` then `mvn spring-boot:run`, and check that Flyway migrates on
  startup, `/actuator/health` reports UP with the Postgres check, and the pending-updates
  endpoint returns seeded data. This is the path a reviewer will actually try, so it has to
  work exactly as the README claims.
- Review `target/site/jacoco/index.html`; drive line and branch coverage as close to 100%
  as the slice allows, and explicitly name anything deliberately left uncovered.
- **JavaDoc is enforced by the build, not by intent:** `maven-compiler-plugin` runs with
  `-Xdoclint:all,-missing` escalated to `-Xdoclint:all` for `src/main`, so a malformed or
  incomplete doc comment fails compilation. This needs no extra plugin —
  `maven-javadoc-plugin` is *not* in `~/.m2` and would require network, whereas doclint
  ships with the JDK and keeps the build offline.
- Confirm `DESIGN.md` stays within 1–3 pages; trim rather than append if it grows.
- Check every Mermaid block parses and renders (fenced ```mermaid). Verify the five C4
  diagrams specifically — L1, L2, the two L3s, L4 — since Mermaid's C4 support is
  experimental; if any renders badly, convert that one to the `flowchart` fallback rather
  than leaving a broken block in a submission.
- Confirm all four C4 levels are present and that each one is at a genuinely different level
  of abstraction — no container-level detail leaking into the context diagram.
- Re-read the spec's five required section headings against `DESIGN.md` — all five present,
  in order, none padded.

## Guardrails

The spec says *do not over-optimize*, and brevity is graded. No extra frameworks beyond Lombok
(see Amendment 1; Lombok is used only for `@Builder`), no ORM (`JdbcClient` — the queries are few
and better read as SQL), no security/auth layer,
no CI config, no changelog, no Docker image build for the app itself. Persistence and one
REST endpoint are in scope; a general data-access framework and a full API are not.

**Spring Boot raises the main risk to this plan.** It makes it cheap to add a second endpoint,
a service layer, DTO mappers, profiles, and a security config — and the spec says explicitly
*"this is not a full application build"* with a 2–3 hour budget. The framework is here to make
the persistence, wiring and observability story real, not to grow the surface area. If time
runs short, the order of sacrifice is: the REST endpoint first, then Actuator metrics, then
the Testcontainers tests — the domain core, its unit tests and `DESIGN.md` are the parts that
must not be cut, because they carry the reasoning being graded.

If a section of the doc grows past its share of three pages, cut it.

The coverage target is met by keeping the slice small and pure, **not** by adding code to
chase a number or writing assertion-free tests that only execute lines. If some branch is
genuinely not worth a test, say so in the README rather than faking the metric.
