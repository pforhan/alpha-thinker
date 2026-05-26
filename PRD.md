# Product Requirements Document (PRD): Alpha Thinker

## 1. Product Vision
Alpha Thinker is an edge-LLM powered project planning application. It transforms a vague project idea (synopsis) into a structured plan through a series of iterative, synthesized question-and-answer rounds.

## 2. Core User Flow
1. **Ideation**: User provides a brief project synopsis.
2. **Initial Analysis**: The system generates a first set of foundational questions.
3. **Iterative Synthesis**:
    - User answers the current set of questions.
    - Once all questions in a round are answered, the system automatically synthesizes the information and generates a new round of follow-up questions.
    - This process repeats until the plan is sufficiently detailed or completed.
4. **Documentation**: The final synthesized knowledge is exported as a structured document (Markdown).

## 3. Functional Requirements

### 3.1 Project Management
- Create new projects with a synopsis.
- Maintain a list of existing projects.
- Track the progress of projects (e.g., count of unanswered questions).

### 3.2 Iterative Questioning System
- **Round-Based Logic**: Questions are grouped into "Exchange Rounds".
- **Automatic Transition**: Automatically trigger the next round of questions when the current round is fully answered.
- **Answer Management**:
    - Support for updating and editing answers.
    - **Auto-Archive**: Option to immediately archive a question once it has been answered to clean up the user interface.
- **Question Archiving**: Ability to deactivate or archive questions that are no longer relevant.

### 3.3 LLM Integration
- Generate a set of tailored initial questions based on the project synopsis.
- Generate follow-up questions based on the project's current state and previous rounds.

### 3.4 Export
- Synthesize all project data (synopsis, all rounds of questions and answers) into a Markdown formatted document.

## 4. User Interface Requirements

### 4.1 Projects List
- Display summary of all projects.
- Show "unanswered" count for each project to indicate remaining work.
- Quick access to create a new project.

### 4.2 Project Creation
- simple input field for a project synopsis.

### 4.3 Project Detail (Planning Workspace)
- Display the current active round of questions at the top.
- Provide an interface to read, answer, and edit questions.
- Visual indicators for completed/answered questions.
- Toggle for "Auto-Archive" behavior.
- Action to export the final project state.

### 4.4 Task view
* Simple view of background jobs, such as llm processing.

## 5. Non-Functional Requirements
- **Offline First**: The app should rely on edge-LLMs or local mocks to ensure rapid iteration and privacy.
- **Cross-Platform**: Shared business logic and models for android, ios, web.
- **Persistence**: Durable local storage of projects and their history.
