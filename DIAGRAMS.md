# C4 Diagrams — Template Update Advisor

The four C4 levels for this system. They live here rather than in `DESIGN.md` because the design
document is capped at three pages and the brief lists diagrams as a separate deliverable; only the
container view is repeated there, beside the prose it supports.

Each level answers a different question, and none repeats the level above:

| Level | Question | Audience |
|---|---|---|
| 1 · Context | What is this system, and what does it touch? | Anyone |
| 2 · Container | What are the deployable pieces and the data stores? | Engineers, ops |
| 3 · Component | What is inside the pieces that carry the argument? | Engineers |
| 4 · Code | How does the implemented slice hang together? | Reviewers of this submission |

---

## Level 1 — System Context

The point of this view is the **tenancy split**. The Advisor deliberately straddles a
firm-independent zone (template content, shared by everyone) and a per-firm zone (engagement
state, isolated per customer). Almost every decision in the design follows from keeping those
two apart.

```mermaid
C4Context
    title Level 1 — System Context

    Person(practitioner, "Audit Practitioner", "Reviews pending template updates and decides whether to apply or decline each one")
    Person(contentAuthor, "Content Author", "Publishes template updates for a market; reviews generated change summaries")

    System(advisor, "Template Update Advisor", "Shows which engagement files have pending template updates, and explains what each update contains in plain language")

    System_Ext(templateStore, "Product Template Store", "Every version of every product template. Shared across all firms; knows nothing about engagements")
    System_Ext(engagementMgmt, "Engagement Management System", "Creates and loads engagement files (~1 min per load) and processes apply/decline decisions")
    System_Ext(engagementFiles, "Engagement File Store", "Per-firm databases holding the engagement files themselves")
    System_Ext(llm, "LLM Service", "Turns a classified set of changes into readable prose")

    Rel(contentAuthor, templateStore, "Publishes template updates")
    Rel(contentAuthor, advisor, "Reviews generated summaries")
    Rel(practitioner, advisor, "Sees pending updates and what changed")
    Rel(practitioner, engagementMgmt, "Applies or declines an update")

    Rel(templateStore, advisor, "Notifies on publish")
    Rel(engagementMgmt, advisor, "Notifies on create, apply, decline and open")
    Rel(advisor, templateStore, "Reads two versions to diff")
    Rel(advisor, llm, "Requests narration of a change set")
    Rel(engagementMgmt, engagementFiles, "Reads and writes")

    UpdateLayoutConfig($c4ShapeInRow="2", $c4BoundaryInRow="1")
```

Note what is **absent**: no arrow from the Advisor to the Engagement File Store. The Advisor never
reads an engagement file. That absence is the design.

---

## Level 2 — Container

Both schemas sit in one PostgreSQL cluster. That co-location is what allows the read path to be a
single join rather than an application-level join across two stores, and it is the detail worth
looking for in this diagram.

See `DESIGN.md` for this diagram in context; it is not duplicated here.

---

## Level 3 — Component

Two containers carry the architectural argument, so both get a component view.

### 3a — Change Analysis Worker (global plane)

Four collaborators, each answering exactly one question. The separation is not decoration: it is
what confines a language model to phrasing, and what makes "the model invented something" a
detectable condition rather than a risk to be hoped away.

```mermaid
C4Component
    title Level 3a — Change Analysis Worker

    System_Ext(templateStore, "Product Template Store", "Versioned templates")
    System_Ext(llm, "LLM Service", "Phrases a change set")
    ContainerDb(pg, "PostgreSQL", "template_change schema", "Shared summaries")

    Container_Boundary(worker, "Change Analysis Worker") {
        Component(consumer, "TemplatePublishedConsumer", "Spring", "Receives publish events and precompute requests")
        Component(client, "TemplateStoreClient", "Spring", "Fetches the two versions and their diff")
        Component(classifier, "PathBasedChangeClassifier", "Java", "WHAT changed — deterministic, stable ids")
        Component(narrator, "LlmChangeNarrator", "Java", "HOW to say it — the only place a model is used")
        Component(validator, "SummaryValidator", "Java", "WHETHER to trust it — citations, coverage, figures")
        Component(renderer, "DeterministicChangeRenderer", "Java", "The fallback: cannot fail, cannot fabricate")
        Component(service, "ChangeSummaryService", "Java", "Composes the above; caches only validated output")
        Component(repo, "JdbcChangeSummaryRepository", "Spring JDBC", "The only component touching the database")
    }

    Rel(consumer, client, "Requests diff")
    Rel(client, templateStore, "Reads versions")
    Rel(consumer, service, "Summarise")
    Rel(service, classifier, "Classify diff")
    Rel(service, narrator, "Narrate")
    Rel(narrator, llm, "Calls")
    Rel(service, validator, "Validate against change set")
    Rel(service, renderer, "Fall back when invalid or unavailable")
    Rel(service, repo, "Read / write summary")
    Rel(repo, pg, "SQL")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

### 3b — Update Advisor API (tenant plane)

```mermaid
C4Component
    title Level 3b — Update Advisor API

    Person(practitioner, "Audit Practitioner", "Dashboard user")
    ContainerDb(pg, "PostgreSQL", "firm_<id> + template_change", "Engagement state and shared summaries")

    Container_Boundary(api, "Update Advisor API") {
        Component(controller, "PendingUpdateController", "Spring MVC", "GET /api/firms/{firmId}/engagements/pending-updates")
        Component(query, "PendingUpdateQueryService", "Java", "Assembles rows; never loads an engagement file")
        Component(resolver, "PendingUpdateResolver", "Java", "Pure rule: is anything pending, and over what range")
        Component(engagementRepo, "JdbcEngagementStateRepository", "Spring JDBC", "Projected engagement state")
        Component(summaryRepo, "JdbcChangeSummaryRepository", "Spring JDBC", "Shared summaries")
        Component(metrics, "MetricsConfig", "Micrometer", "projection.drift, cache hits, validation failures")
    }

    Rel(practitioner, controller, "GET pending updates", "HTTPS")
    Rel(controller, query, "Delegates")
    Rel(query, resolver, "Resolve range per engagement")
    Rel(query, engagementRepo, "Read projection")
    Rel(query, summaryRepo, "Read summary for (template, from, to)")
    Rel(engagementRepo, pg, "SQL")
    Rel(summaryRepo, pg, "SQL")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

In both views the JDBC adapters are the only components touching the database. That is the
ports-and-adapters split drawn out: the rules in the middle have no infrastructure dependency,
which is why they are testable without any.

---

## Level 4 — Code

C4 normally treats this level as optional and rarely worth maintaining, because it drifts from the
code within weeks. It earns its place here for one reason: it describes code that actually exists
in this submission, so it is the bridge between the design and Part 2 rather than an aspiration.

```mermaid
classDiagram
    direction LR

    class PendingUpdateResolver {
        +resolve(EngagementTemplateState, TemplateVersion) Optional~PendingUpdate~
    }
    class EngagementTemplateState {
        <<record>>
        +currentVersion TemplateVersion
        +declinedVersions Set~TemplateVersion~
        +withApplied(TemplateVersion) EngagementTemplateState
        +withDeclined(TemplateVersion) EngagementTemplateState
    }
    class PendingUpdate {
        <<record>>
        +fromVersion TemplateVersion
        +toVersion TemplateVersion
        +declinedInRange List~TemplateVersion~
    }

    class ChangeSummaryService {
        +summarize(ChangeSet) NarratedSummary
    }
    class ChangeClassifier {
        <<interface>>
        +classify(...) ChangeSet
    }
    class ChangeNarrator {
        <<interface>>
        +narrate(ChangeSet) NarratedSummary
    }
    class SummaryRenderer {
        <<interface>>
        +render(ChangeSet) NarratedSummary
    }
    class SummaryCache {
        <<interface>>
        +get(ChangeSummaryKey) Optional~NarratedSummary~
        +put(ChangeSummaryKey, NarratedSummary)
    }
    class SummaryValidator {
        +validate(ChangeSet, NarratedSummary) ValidationResult
    }

    class PathBasedChangeClassifier
    class LlmChangeNarrator
    class DeterministicChangeRenderer
    class JdbcChangeSummaryRepository
    class ChangeSet {
        <<record>>
        +records List~ChangeRecord~
    }
    class NarratedSummary {
        <<record>>
        +bullets List~SummaryBullet~
    }
    class ChangeSummaryKey {
        <<record>>
        +templateId TemplateId
        +fromVersion TemplateVersion
        +toVersion TemplateVersion
    }

    PendingUpdateResolver ..> EngagementTemplateState : reads
    PendingUpdateResolver ..> PendingUpdate : produces

    ChangeSummaryService --> ChangeNarrator : depends on abstraction
    ChangeSummaryService --> SummaryValidator
    ChangeSummaryService --> SummaryRenderer : fallback
    ChangeSummaryService --> SummaryCache
    ChangeSummaryService ..> ChangeSummaryKey : keys by

    ChangeClassifier <|.. PathBasedChangeClassifier
    ChangeNarrator <|.. LlmChangeNarrator
    SummaryRenderer <|.. DeterministicChangeRenderer
    SummaryCache <|.. JdbcChangeSummaryRepository

    ChangeClassifier ..> ChangeSet : produces
    SummaryValidator ..> ChangeSet : checks against
    SummaryValidator ..> NarratedSummary : checks
```

Two things this view is meant to make obvious.

`ChangeSummaryService` points only at interfaces. No language-model client is constructed anywhere
in the core, which is why the whole summarisation path is testable offline — the SOLID argument
and the coverage figure are the same decision seen twice.

`ChangeSummaryKey` holds a template and two versions and nothing else. There is no firm and no
engagement in it, and there is no column for one in the table behind it. The claim that summaries
are safe to share across customers is enforced by the type and by the schema, not by a convention
someone has to remember.
