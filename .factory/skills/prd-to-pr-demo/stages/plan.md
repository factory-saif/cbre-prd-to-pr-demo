# Stage: plan

Split PRDs into small stories, each with an implementation plan, and release
stories to Dev when their dependencies are done.

## Steps

### 0. Close out merged work (do this first)

1. List `Demo` stories (issues with a parent) that are not `Done` and have a
   trusted `## Pull Request` comment.
2. For each one, check its pull request:
   `gh pr view <url> --repo factory-saif/prd-to-pr-demo --json state,mergedAt`.
   When it is `MERGED`, move the story to `Done` and remove `bug` and
   `Release`.
3. When every story under a parent is `Done`, move the parent to `Done`.

Do this before section B, so stories that depend on a merged story are
released in the same run.
### A. New PRDs

1. List open `Demo` issues that have no parent issue and have a trusted
   `## Structured PRD` comment (see the signal rule in `SKILL.md`). Skip an
   issue when its newest trusted signal comment is `## PRD Questions`. Add
   `PRD` where it is missing, because a delegated PRD session may not be able
   to set labels.
2. For each one, skip it if a comment starts with `## Stories`. Otherwise:
   1. Read the newest trusted `## Structured PRD` comment.
   2. Read the code the PRD touches (`src/main/java/com/example/facilities/`,
      `src/main/resources/static/`, and the matching tests) so each plan names
      real files, classes, and methods.
   3. Split the PRD into 1 to 3 stories. Each story is one pull request that
      one Dev session can finish: a coherent change with its tests, usually
      under about 300 changed lines. Prefer stories that can be built in
      parallel. Order them so that an API story comes before a UI story that
      depends on it.
   4. Create each story as a sub-issue of the parent, in the `Demo`
      project, team `SED`, state `Todo`. Title: a short imperative sentence.
      The description starts with the heading `## Implementation Plan` and
      contains:
      - **Parent**: the parent issue key.
      - **Goal**: one sentence.
      - **Acceptance criteria**: the PRD criteria this story covers, copied
        with their numbers.
      - **Files**: every file to create or change, with the change in one line
        each.
      - **Tests**: the unit or MockMvc tests to add or update, naming the test
        class and the behavior each test proves.
      - **Depends on**: the keys of stories that must merge first, or `none`.
      - **Non-goals**: what this story leaves to other stories.
   5. Add `Story` to every story whose **Depends on** is `none`.
   6. If the parent carries `Experiment`, add `Experiment` to
      exactly one story: the first story released to Dev that changes
      user-visible behavior.
   7. Post one comment on the parent whose first line is `## Stories`, listing
      each story key, title, and dependency.
   8. Move the parent to `In Progress`.

### B. Release waiting stories

1. List open `Demo` stories (issues with a parent) that have no
   `Story` label.
2. For each one, read **Depends on** from its plan. Add `Story` when
   every listed story is in `Done`.

## Rules

- If the PRD is too vague to plan, comment on the parent with the missing
  decision and do not post `## Stories`.
- Never edit or delete stories that already exist, except for the state and
  label changes in section 0. Never modify the repository.
