# Scrollbar Components

Custom scrollbar implementations with modern styling and smooth behavior.

## Components

- **AeroScrollBarUI**: Aero-themed scrollbar appearance
- **ModernScrollBarUI**: Clean scrollbar design with animations

## Features

- Custom visual design
- Smooth scrolling
- Theme-aware styling
- High-DPI support
- Accessibility compliance

## Usage

```java
// Apply modern scrollbar to a JScrollPane
JScrollPane scrollPane = new JScrollPane(content);
scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
```
