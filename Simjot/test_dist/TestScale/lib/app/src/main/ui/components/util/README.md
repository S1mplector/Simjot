# Utility Components

Helper classes and utilities for UI development and management.

## Components

- **EditorUIUtils**: Common UI operations and helper functions
- **DragController**: Drag and drop handling implementation

## Features

- Common UI patterns
- Drag and drop support
- Helper methods for frequent operations
- Performance optimization utilities
- Theme management helpers

## Usage

```java
// Create toolbar buttons with utility
ToolbarIconButton boldBtn = EditorUIUtils.createToolbarButton("bold", "Bold Text");
ToolbarIconButton italicBtn = EditorUIUtils.createToolbarButton("italic", "Italic Text");

// Setup drag and drop
DragController dragController = new DragController(component);
dragController.enableDragAndDrop();
```
