# Icon Components

Icon management and rendering system with vector-based support.

## Components

- **AppIcon**: Application icon handling and management
- **VectorIconPainter**: SVG-based icon rendering
- **ImageIconRenderer**: Image icon processing and optimization
- **ModernFileIcons**: File type icons with modern design

## Features

- Vector-based rendering
- Resolution independence
- Theme-aware coloring
- Performance optimization
- Custom icon support

## Usage

```java
VectorIconPainter painter = new VectorIconPainter();
Icon icon = painter.loadIcon("/icons/save.svg");
icon = painter.applyTheme(icon, Theme.Variant.AERO);
```
