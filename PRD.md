# Product Requirements Document (PRD): Alpha Thinker

## 1. Product Vision
Alpha Thinker is an edge-LLM powered project planning application. It transforms a vague project idea (synopsis) into a detailed plan through a series of iterative, synthesized questions and answers.

## 2. Core User Flow
1. **Ideation**: User provides a brief project synopsis.
2. **Initial Analysis**: The system generates a project title and a set of initial questions along with suggested answers if they are obvious.
3. **Iterative Synthesis**:
    - User is presented a list of questions on cards, roughly enough to fill the screen without scrolling, though on small screens it's acceptable to scroll to see all questions.
    - As the user answers or dismisses questions the response is recorded, moved to an "answered" list, and unseen ones replace them.
    - When the list of unseen questions gets low, or when the synposis changes, run a job to ask the local LLM to create more based on the synposis and answered / dismissed questions.
    - This process repeats until the plan is sufficiently detailed or completed.
4. **Documentation**: The final synthesized knowledge can be exported as a structured markdown document.

## 3. Functional Requirements

### 3.1 Project Management
- Maintain a list of existing projects.
- Track the progress of projects (e.g., count of unanswered questions).
- Create new projects with a synopsis.
- Edit a project / resume the Q & A

### 3.2 Iterative Questioning System
- **Automatic Generation**: Automatically trigger the generation of more questions as needed.
- **Answer Management**: Support for editing answers.
- **Question Archiving**: The user has the ability to deactivate or archive questions that are no longer relevant. Provide a user option to also allow the app to do this, either automatically when the synopsis or precending questions change, either all of them or based on LLM evaluation.

### 3.3 LLM Integration
- Generate a set of tailored initial questions based on the project synopsis.
- Generate follow-up questions based on the project's current state and previous rounds.

### 3.4 Export
- Synthesize all project data (synopsis, all rounds of questions and answers) into a Markdown formatted document. It should be in a format like this:
```md
# {{project.synopsis}}

## Overview
{{project.synopsis}}

{{#each project.exchangeRounds}}
## Round {{round}} {{#if isActive}}(Active){{else}}(Archived){{/if}}
> Generated: {{createdAt}}

{{#each questions}}
### Q: {{text}}

{{#with answer}}
| **Answer:** | {{text}} |
|-------------|----------|
| **Answered:** | {{answeredAt}} |
{{#if modifiedAt}}| **Modified:** | {{modifiedAt}} |{{/if}}
{{else}}
| **Status:** | unanswered |
|------------|------------|
{{/with}}

{{/each}}
{{/each}}
```

## 4. User Interface Requirements

### 4.1 Projects List
- Display summary of all projects, including synopsis.
- Show "unanswered" count for each project to indicate remaining work.
- Quick access to create a new project.

### 4.2 Project Creation
- simple input field for a project synopsis.

### 4.3 Project Detail (Planning Workspace)
- Default view shows synopsis and a screenful of unanswered questions
- Provide an interface to read, answer, and edit questions.
- Ability to view unanswered questions, archived questions, or both.
- Visual indicators for completed/answered questions.
- Picker for "Auto-Archive" behavior -- none / all / ask LLM.
- Action to export the final project state.

## 5. Non-Functional Requirements
- **Offline First**: The app should rely on edge-LLMs or local mocks to ensure rapid iteration and privacy.
- **Cross-Platform**: Shared business logic and models via Kotlin Multiplatform (KMP).
- **Persistence**: Durable local storage of projects and their history.
