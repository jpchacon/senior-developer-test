# AI Session History

Deliverable 3 of the brief. A record of how this submission was built with AI assistance, and
of the judgment applied around it.

**Tooling:** Claude Code (Opus 5), one session, working directly in the repository — reading and
writing files, running Maven and Docker, executing the test suite and the Postman collection.

**On the raw transcript.** The verbatim JSONL log is deliberately not committed. It contains the
full text of Caseware's take-home brief, which is not mine to publish, along with local absolute
paths and personal identifiers. This document is a curated account instead. The raw log is
retained locally and can be shared privately on request.

---

## How the work was sequenced

1. **Read the brief.** The PDF was extracted to Markdown so the constraints could be quoted
   precisely rather than paraphrased from memory.
2. **Plan before code.** A written plan was produced and then revised six times before any
   implementation began. Every revision came from me, not the model: TDD with a coverage gate;
   Clean Code, SOLID and JavaDoc; C4 diagrams at all four levels; PostgreSQL with Docker for local
   testing; Spring Boot and Maven; and keeping the plan in the repository as a working document.
3. **Verify the toolchain before committing to it.** The plan's first execution step was an
   offline dependency probe, on the reasoning that a missing artifact is cheap to discover in the
   first minute and expensive in the second hour. That step paid for itself immediately (below).
4. **Build test-first.** The failing test was observed before the implementation in each case;
   the first resolver test was run and seen red before `PendingUpdateResolver` existed.
5. **Verify against reality, not against the model's claims.** The application was run, the
   endpoint exercised, migrations confirmed applied, and the Postman collection executed with
   Newman. Nothing in this submission is reported as working on the strength of it having compiled.

---

## Where AI helped

- **Drafting volume at consistent quality.** 45 main classes, 21 test classes, migrations, two
  design documents and five C4 diagrams inside one session. The bottleneck became review, not
  typing.
- **Holding a convention across every file.** Records validating in compact constructors,
  defensive copies, JavaDoc on public API, tests named as behaviour sentences — applied uniformly
  rather than drifting as fatigue set in.
- **Surfacing consequences of a decision quickly.** When PostgreSQL replaced a key-value store,
  the knock-on effects — fan-out becoming `SELECT DISTINCT`, the read path collapsing to a single
  join, tenancy needing an explicit model — were laid out immediately rather than discovered later.
- **Turning constraints into executable checks.** The "no customer data in shared summaries"
  invariant became a primary key with no column for a firm id; "an update never runs backwards"
  became a `CHECK` constraint and a Postman assertion.

## Where AI was wrong, and what caught it

This is the section worth reading. In every case the error was caught by a mechanical check, not
by inspection.

| What the model asserted | Reality | What caught it |
|---|---|---|
| Spring Boot 4.1.0 manages Testcontainers versions | It does not; the BOM must be imported explicitly | Offline dependency probe, minute one |
| Boot 4 uses Jackson 2 (`com.fasterxml.jackson`) | Boot 4 ships **Jackson 3** (`tools.jackson`), with unchecked exceptions | Compile failure |
| `@WebMvcTest` lives in `spring-boot-test-autoconfigure` | Boot 4 moved it to `spring-boot-starter-webmvc-test` | Compile failure |
| `Set.of(...)` was fine for the validator's allowed figures | Threw at runtime: a change count of 3 collided with version 3 | Unit test |
| `-Xdoclint:all` was the right strictness | Demanded full tags on private methods; noise, not contract | Build output |
| Lombok was cleanly integrated (`mvn verify` passed) | **A clean build was broken**; incremental compilation had hidden it | `mvn clean compile` |
| `provided` scope keeps Lombok out of the artifact | Boot's repackage bundles provided-scope deps; 2 MB shipped in `BOOT-INF/lib` | Inspecting the jar |
| The API was complete | Invalid input returned **500**, not 400 | Writing the Postman validations |

Two of these are worth dwelling on.

**The Lombok clean-build failure** is the one that would have reached a reviewer. `mvn verify`
passed because only changed files recompiled; `mvn clean compile` failed with 92 doclint warnings
under `-Werror`, all of them Lombok's generated builder members having no JavaDoc. The lesson is
not about Lombok — it is that a green incremental build is not evidence of a green build.

**The 500-instead-of-400 defect** was found by writing tests from outside the process. Eighty-three
unit and integration tests had not caught it, because they all exercised the domain from within,
where `IllegalArgumentException` is the correct behaviour. Only an HTTP client asked what the
*caller* sees. The fix was a one-class handler; the choice worth noting is that I fixed it rather
than writing a Postman test asserting 500 was correct, which would have encoded the defect as
intended behaviour.

## Where I redirected the model

- **The stack was my decision, not the model's.** Its initial plan proposed a dependency-free
  library slice. Spring Boot, Maven, PostgreSQL, Docker, Lombok, C4 and the TDD coverage gate were
  each added by me, and the plan was amended each time rather than rewritten to look prescient.
- **Scope discipline had to be imposed and then re-imposed.** The model's own plan flagged that
  Spring Boot makes over-building cheap, and recorded an explicit order of sacrifice. The API is
  one endpoint; apply/decline belongs to the engagement system.
- **Lombok was scoped down.** The instruction was to apply the Builder pattern to objects with many
  parameters. Applied literally that would mean annotating every record; the codebase is records
  throughout, so most of Lombok is redundant against them. Builders went on the seven types where
  positional construction is a genuine hazard, and the six small ones kept plain constructors.
- **Publishing decisions were mine.** The brief's own document was removed from this public
  repository, and the raw transcript was never added.

## Where AI should not be trusted in this domain

The same boundary the system itself enforces, and for the same reason.

A language model in this design decides **only how to phrase** an already-derived set of changes.
It never determines whether an update exists — that is a projection built from events, and it is
deterministic. It never determines what changed — that is a structural diff. And it never
recommends applying or declining, because that is professional judgment, and the brief is explicit
that AI augments practitioners rather than replacing them.

Everything the model produces is checked mechanically before a practitioner sees it: every
sentence cites change-record ids, so a claim about a change that does not exist, a change left
unmentioned, and a figure with no basis in the diff are all detectable rather than merely unlikely.
`SummaryValidator` feeds it deliberately hallucinated output in the test suite to prove the check
bites. When validation fails the deterministic renderer takes over, and that output is
deliberately not cached, so an outage degrades one request instead of permanently seeding a
plainer summary for every firm on that version pair.

Notably, no model judges another model's output anywhere in this system.

## How I would guide other engineers using AI here

1. **Make the model prove its claims, not state them.** Every factual assertion above that turned
   out to be wrong was about a version, an API, or a package name — precisely the class of claim a
   model is confident about and frequently wrong about. A ten-second command settles it.
2. **Front-load the cheap verification.** The dependency probe was the first execution step by
   design, and it caught the first error immediately.
3. **Never accept an incremental green build as evidence.** Run `clean` before believing anything.
4. **Test from outside the process.** The unit suite was thorough and still missed a defect visible
   to the first HTTP client that asked.
5. **Do not let generated tests define correct behaviour.** When a test and the intended behaviour
   disagree, one of them is a bug; deciding which is a judgment call, and it is not the model's.
6. **Keep the audit trail honest.** `PLAN.md` records what was planned, what changed after
   approval, and where a later decision weakened an earlier claim — including that JavaDoc
   completeness is no longer build-enforced. A plan edited to look prescient is worth nothing in a
   design review.
