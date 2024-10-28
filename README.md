Here's a well-structured and professional README for your Set Game project:

---

# Set Game

This project implements a digital version of the classic card game **Set**. Designed to test your pattern recognition and quick-thinking skills, this interactive game challenges players to identify "sets" of three cards from a grid based on specific attributes: **shape, color, number,** and **shading**.

## Table of Contents

1. [Features](#features)
2. [Installation](#installation)
3. [Usage](#usage)
4. [Class Descriptions](#class-descriptions)
5. [Configuration](#configuration)
6. [Additional Notes](#additional-notes)
7. [Contributing](#contributing)
8. [License](#license)

---

## Features

- **Core Gameplay**: Faithfully recreates the classic *Set* game mechanics and rules.
- **User Interface**: Built with Java Swing, offering an intuitive and visually appealing experience.
- **Player Modes**: Supports both human and computer players for versatile gameplay.
- **Timer and Scoring**: A timer challenges players to find sets quickly, with a scoring system to track progress.
- **Input Handling**: Allows players to select cards using keyboard input.
- **Game Configuration**: Customize game settings through a configuration file for a personalized experience.

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/HillelCharbit/set-game-concurrency-project.git
   ```
2. Navigate to the project directory:
   ```bash
   cd set-game-concurrency-project
   ```
3. Compile the Java files:
   ```bash
   javac -d bin src/*.java
   ```
4. Run the game:
   ```bash
   java -cp bin Main
   ```

## Usage

After running the game, a window will appear displaying the game grid. Use the keyboard to select cards and identify sets. The timer and scoring system will track your performance as you play. Customize gameplay options via the configuration file if needed.

## Class Descriptions

### Config
Handles game configuration settings, loading properties from a `config.properties` file.

### Env
Manages the game environment, holding references to core objects like the logger, configuration, UI components, and utility classes.

### InputManager
Processes keyboard input, translating it to corresponding grid slots and dispatching selections to the correct player (human or computer).

### Main
Serves as the game's entry point, initializing game components, setting up the environment, and starting the dealer thread.

### ThreadLogger
Provides logging for thread events, recording start and stop messages for debugging purposes.

### UserInterface
Defines methods for rendering the game's graphical user interface.

### UserInterfaceDecorator
Enhances the **UserInterface** with additional functionalities, such as logging and timing.

### UserInterfaceSwing
Implements the **UserInterface** using Java Swing, handling the game's visual components and player interactions.

### Util
Provides helper methods for card operations and set validation.

### UtilImpl
Implements the **Util** interface, offering specific functionality for identifying sets and manipulating cards.

## Configuration

Game settings can be adjusted in the `config.properties` file, including:
- **Timer Duration**: Set the timer limit for each round.
- **Game Mode**: Switch between human and computer player modes.
- **Display Options**: Customize visual aspects of the game grid.

## Additional Notes

- The project utilizes **Java Swing** for the graphical user interface, providing a dynamic and interactive experience.
- Extensive **logging** is incorporated for tracking game events, supporting debugging, and enhancing game stability.
- **Customizability**: Game parameters are easily configurable, allowing for tailored gameplay experiences.

## Contributing

Contributions are welcome! If you'd like to improve the project, please follow these steps:

1. Fork the repository
2. Create a new branch:
   ```bash
   git checkout -b feature/YourFeature
   ```
3. Commit your changes:
   ```bash
   git commit -am 'Add YourFeature'
   ```
4. Push the branch:
   ```bash
   git push origin feature/YourFeature
   ```
5. Open a Pull Request

Please open an issue to discuss potential changes or improvements before starting work on a new feature.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---
