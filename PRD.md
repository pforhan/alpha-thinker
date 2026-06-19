# Product Requirements Document (PRD): Alpha Thinker

## 1. Product Vision
Alpha Thinker is a project planning application that transforms a vague project idea (synopsis) into a detailed plan through iterative, synthesized questions. It is available in two editions: **Alpha Thinker Edge**, powered by a local LLM, and **Alpha Thinker Lite**, a lightweight version focused on manual user-driven planning.

## 2. Product Editions

Both Editions share the same general UI, but differ in source of question material and ability to generate content.

### 2.0 Shared Features
- **Custom Questions**: Users can extend the question pool by adding their own questions, specifying whether they are unique to the current project or added to a global pool for use in all projects.

### 2.1 Alpha Thinker Edge
- **Feature Set**: Full iterative synthesis using a local edge-LLM.
- **Intelligence**: Automatically generates tailored questions and suggested answers based on the specific project synopsis and history. Includes any global questions added by the user.
- **Automation**: LLM-driven question archiving and synopsis updates.

### 2.2 Alpha Thinker Lite
- **Feature Set**: Manual planning workspace.
- **Intelligence**: Uses a robust set of 20 hardcoded "Seed Questions" to kickstart the planning process, along with any global questiosn added.
- **User-Powered UI**: The user is responsible for creating their own follow-up questions or modifying the plan without automated synthesis.

## 3. Core User Flow
1. **Ideation**: User provides a brief project synopsis.
2. **Initial Analysis**: 
    - **Edge**: The LLM generates an editable title and initial tailored questions.
    - **Lite**: The system applies the project title from the synopsis and loads the 20 Seed Questions.
3. **Iterative Synthesis**:
    - **Active Round Focus**: Current unanswered questions are prioritized at the top of the workspace.
    - User is presented a list of questions on cards, roughly enough to fill the screen without scrolling (though scrolling is acceptable on small screens).
    - As the user answers or dismisses questions, the response is recorded, the card moves to an "answered" list, and unseen questions replace them.
    - **Automated Transition (Edge)**: Once all questions in the current round are answered, the system automatically triggers the LLM to generate the next round.
    - **Manual Expansion (Lite)**: The user manually adds new questions or works through the fixed seed list.
4. **Documentation**: The final synthesized knowledge is exported as structured markdown.

## 4. Functional Requirements

### 4.1 Project Management
- Maintain a list of existing projects.
- Track progress (unanswered vs. answered counts).
- Create new projects with a synopsis.
- Edit a project / resume the Q & A.

### 4.2 Iterative Questioning System
- **Answer Management**: Support for reading, answering, and editing answers.
- **Question Archiving**: Users can manually deactivate or archive questions.
- **Immediate Archiving**: Option to archive questions immediately upon being answered.
- **Auto-Archive (Edge Only)**: Provide a user app-wide setting to determine behavior when the synopsis or preceding questions change: a) do nothing, b) clear all prior questions and answers, or c) ask the LLM if each question is still relevant.
- **Automatic Generation (Edge Only)**: Automatically trigger the generation of more questions as needed.

### 4.3 LLM Integration (Alpha Thinker Edge Only)
- Generate a set of tailored initial questions based on the project synopsis.
- Generate follow-up questions based on the project's current state and previous answerss.

### 4.4 Seed Questions (Alpha Thinker Lite Only)
The Lite version includes these 20 generic questions to guide the user:
1. What is the primary problem this project solves?
2. Who is the ideal user or beneficiary?
3. What is the single most important goal?
4. What are three key milestones for the first month?
5. What resources (time, money, tools) are currently available?
6. What resources are still needed?
7. What is the target completion date?
8. What are the top three risks to success?
9. How will you know if the project is successful?
10. What is the "Minimum Viable Product" (MVP) version?
11. What are the key technical constraints or requirements?
12. Who are the primary stakeholders and decision-makers?
13. What similar projects or competitors have you looked at?
14. What is the long-term vision for this project?
15. What are the non-negotiable features or qualities?
16. How will this project be maintained or supported later?
17. What is the estimated total budget?
18. Are there any legal, ethical, or compliance factors?
19. How will you promote or distribute the final result?
20. What is the very first step you need to take?

### 5.1 Projects List
- Display summary of all projects, including synopsis.
- Show "unanswered" count for each project to indicate remaining work.
- Empty state provides a clear Call-To-Action (CTA) to create a new project.

### 5.2 Project Creation
- Simple input field for a project synopsis.

### 5.3 Project Detail (Planning Workspace)
- Default view shows synopsis and a screenful of unanswered questions.
- **Interactive Question Cards**: Each card includes the question text, timestamp, and an editable answer area.
- **Answer Revision UI**: Provide a list of prior versions of an answer with date/time stamps, allowing the user to view or restore previous versions.
- Ability to view unanswered questions, archived questions, or both.
- Ability to ask a new question, optionally adding to global list.
- Visual indicators for completed/answered questions.
- Picker for "Auto-Archive" behavior (Edge Only).
- Action to export the final project state.

### 5.4 Global Question Management
- **Global Question Repository**: A dedicated view to manage the pool of user-defined questions that can be applied to any project.
- **Management Actions**: Users can add new questions to the global pool, edit existing ones, or delete them.
- **Discovery**: When creating a new project or adding questions to an existing one, users can browse and select from the global pool.

### 5.5 System / Debug Workspace
- **LLM Interaction Log**: A specialized view showing all historical LLM interactions, including the exact prompt sent, the response received, and the timing/latency for each.
- **Debug LLM Console (Debug builds only)**: An interactive interface allowing developers to manually initiate LLM sessions, test prompts, and verify model behavior in real-time.
- **Task Manager**: Visibility into long-running background tasks (like a cohesive document rewrite) with status and completion indicators.

## 6. Export
- Synthesize all project data into a Markdown document.
- Support for exporting via the system's native file picker or share sheet.
- Default document format:
```md
# {{project.synopsis}}

## Overview
{{project.synopsis}}

{{#with groupedQuestions}}
## Answered Questions
{{#each groupedQuestions.answered}} // sorted by answer date
*Q: {{questionText}}*
{{answerText}}
{{/each}}
## Remaining Questions
{{#each groupedQuestions.unanswered}} // sorted by generation date
*Q: {{questionText}}*
{{/each}}
## Archived Questions
{{#each groupedQuestions.archived}} // sorted by archive date
*Q: {{questionText}}*
{{/each}}
{{/with}}
```
- Alpha Thinker Edge can also ask the LLM to rewrite the above as a cohesive doc.

## 7. Non-Functional Requirements
- **Offline First**: The app should rely on edge-LLMs or local mocks to ensure rapid iteration and privacy.
- **Cross-Platform**: The application should support multiple platforms (e.g., Mobile and Desktop) with shared business logic.
- **Persistence**: Durable local storage of projects and their history.

## 8. Open Questions & Planning TODOs
*(None currently - all moved to IMPLEMENTATION-PLAN.md or resolved)*


