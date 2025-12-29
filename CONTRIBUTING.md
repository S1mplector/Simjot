# Contributing to Simjot

Thank you for your interest in contributing to Simjot! This document provides guidelines for contributors who want to help improve the project.

## Project Overview

Simjot is a creative wellness application built with pure Java Swing, featuring journaling, poetry analysis, AI companionship, and mindfulness tools.

## Ways to Contribute

### Code Contributions
- **Bug fixes** - Address issues reported in the project
- **New features** - Implement functionality that aligns with the project's vision
- **Performance improvements** - Optimize existing code for better performance
- **UI/UX enhancements** - Improve the user interface and experience

### Documentation
- **Documentation improvements** - Enhance existing documentation
- **Tutorials** - Create guides for users and developers
- **Translation** - Help with internationalization efforts

### Testing
- **Unit tests** - Write tests for core functionality
- **Integration tests** - Test component interactions
- **Bug reports** - Report issues with detailed reproduction steps

## Development Setup

### Prerequisites
- **Java 17+** - Ensure JDK 17 or higher is installed
- **Maven 3.8+** - For building and dependency management
- **Git** - For version control

### Build Instructions
```bash
# Clone the repository
git clone <https://github.com/S1mplector/Simjot.git>
cd Simjot

# Build the project
mvn clean package

# Run tests
mvn test

# Run the application
java -jar target/Simjot-1.0.0.jar
```

## Code Guidelines

### Java Code Style
- Follow standard Java conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Keep methods focused and concise

### Architecture Principles
- **Modular design** - Maintain clear separation of concerns
- **MVC pattern** - Use Model-View-Controller for UI components
- **Event-driven** - Leverage the event bus for component communication
- **Thread safety** - Ensure proper synchronization where needed

### UI Guidelines
- **Swing best practices** - Follow Java Swing conventions
- **Consistent theming** - Use the established theme system
- **Accessibility** - Consider users with different needs
- **Performance** - Optimize for responsive UI

## Submission Process

### 1. Fork and Branch
```bash
# Fork the repository
git clone <your-fork-url>
cd Simjot
git checkout -b feature/your-feature-name
```

### 2. Development
- Make your changes following the guidelines
- Add tests for new functionality
- Update documentation as needed
- Ensure all tests pass

### 3. Commit
```bash
git add .
git commit -m "feat: add your feature description"
```

### 4. Pull Request
- Push to your fork
- Create a pull request with:
  - Clear description of changes
  - Testing information
  - Any breaking changes noted

## Testing Requirements

### Unit Tests
- Write tests for new functionality
- Ensure existing tests still pass
- Aim for good test coverage

### Manual Testing
- Test UI changes manually
- Verify functionality across different platforms
- Check performance impact

## Code Review Process

### Review Criteria
- **Functionality** - Does the code work as intended?
- **Quality** - Is the code well-written and maintainable?
- **Testing** - Are tests adequate and passing?
- **Documentation** - Is documentation updated?
- **Performance** - Does it impact application performance?

### Review Guidelines
- Be constructive and respectful
- Focus on the code, not the person
- Provide specific suggestions for improvement
- Acknowledge good work

## Project Structure

```
Simjot/
├── Simjot/src/main/
│   ├── core/           # Domain logic and business rules
│   ├── infrastructure/ # System services and utilities
│   ├── ui/             # User interface components
│   └── resources/      # Assets and configuration
├── Simjot/tests/       # Test suite
├── scripts/           # Build and utility scripts
└── docs/              # Project documentation
```

## Communication

### Issue Reporting
- Use the project's issue tracker
- Provide detailed reproduction steps
- Include system information (OS, Java version)
- Add relevant logs or screenshots

### Questions and Discussions
- Check existing documentation first
- Search for similar issues or discussions
- Be specific in your questions
- Provide context when possible

## License

By contributing to Simjot, you agree that your contributions will be licensed under the same terms as the project. Please review the main LICENSE.md file for details.

## Recognition

Contributors are recognized in:
- Project documentation
- Release notes
- Contributor acknowledgments

Thank you for helping make Simjot better!

---

**Last Updated**: December 2024  
**Project Version**: 1.0.0
