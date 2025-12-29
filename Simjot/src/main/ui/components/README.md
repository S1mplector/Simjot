# UI Components Architecture

The UI components system provides a modern and customizable interface framework for Simjot with Aero-inspired design and theming capabilities. This component library is designed to be reusable across Java Swing applications with a focus on modern aesthetics, performance, and accessibility.

## Overview

This custom Swing component library includes:
- **Modern visual design** with glass-morphism effects and smooth animations
- **Theme system** supporting Aero, Light, and Sepia variants
- **High performance** rendering with GPU acceleration and memory optimization
- **Full accessibility** support with keyboard navigation and screen reader compatibility
- **DPI awareness** for high-resolution displays
- **Extensible architecture** for custom components and themes

## Component Hierarchy

### Core Containers
- **AeroPanel**: Glass-morphism panels with blur effects
- **RoundedPanel**: Modern rounded corner containers
- **TranslucentPanel**: Semi-transparent overlays
- **AnimatedGlassPopup**: Smooth animated dialogs

### Input Components
- **ModernTextField**: Sleek text input with validation
- **AeroTextField**: Aero-styled text fields
- **AeroPasswordField**: Secure password entry
- **ModernSpinner**: Numeric input with stepping
- **ModernDatePicker**: Calendar date selection
- **MoodSlider**: Emotional state input

### Interactive Elements
- **MainMenuButton**: Primary navigation buttons
- **RoundedButton**: Modern action buttons
- **RoundedToggleButton**: State toggle buttons
- **ToolbarIconButton**: Icon-based toolbar actions
- **IconMenuButton**: Icon menu items

### Selection Components
- **ModernComboBox**: Dropdown selection with custom rendering
- **ModernCheckBox**: Checkbox with custom styling
- **AeroCheckBoxUI**: Aero-themed checkbox appearance

### Feedback Components
- **SaveIndicatorPanel**: Auto-save status display
- **QuickMoodWidget**: Mood visualization and input

## Theme System

### Theme Variants
```java
public enum Variant {
    AERO,    // Windows 7 Aero glass style
    LIGHT,   // Clean light theme
    SEPIA    // Warm sepia tone
}
```

### Theming Architecture
- **Theme**: Central theme management
- **AeroTheme**: Aero-specific styling
- **AeroLookAndFeel**: Complete Look and Feel implementation
- **UIScalingManager**: DPI-aware scaling

### Custom UI Classes
- **AeroScrollBarUI**: Custom scrollbar styling
- **ModernScrollBarUI**: Clean scrollbar design
- **ModernComboBoxUI**: Dropdown appearance
- **ModernSpinnerUI**: Numeric input styling
- **ModernCheckBoxUI**: Checkbox appearance

## Animation System

### Transition Components
- **FadeTransitionPanel**: Smooth fade animations
- **FadingButton**: Button with fade effects
- **AnimatedGlassPopup**: Dialog animations

### Animation Features
- **Configurable duration**: Adjustable timing
- **Easing functions**: Natural motion curves
- **Performance optimized**: GPU acceleration
- **Memory efficient**: Resource pooling

## Editor Integration

### Rich Text Styling
- **RichTextStyler**: Advanced text formatting
- **EditorUIUtils**: Editor helper functions
- **ImagePasteManager**: Image handling in editor

### Poetry-Specific Features
- **PoetryStyleToolbar**: Poetry formatting tools
- **Meter highlighting**: Visual stress patterns
- **Rhyme visualization**: Color-coded rhymes

## Dialog System

### Dialog Types
- **ConfigDialog**: Configuration interfaces
- **BreathingConfigDialog**: Breathing exercise settings
- **CustomChoiceDialog**: Multi-option selections
- **DetailedMoodDialog**: Mood detail input
- **QuickCaptureDialog**: Rapid entry creation

### Dialog Features
- **Modal blocking**: Proper focus management
- **Keyboard navigation**: Full accessibility
- **Responsive design**: Adaptive sizing
- **Theme integration**: Consistent styling

## Icon System

### Icon Management
- **AppIcon**: Application icon handling
- **VectorIconPainter**: SVG-based icon rendering
- **ImageIconRenderer**: Image icon processing
- **ModernFileIcons**: File type icons

### Icon Features
- **Resolution independence**: Vector-based rendering
- **Theme awareness**: Color adaptation
- **Performance optimized**: Caching and pooling
- **Custom icons**: User-defined icon support

## Utility Components

### Helper Classes
- **EditorUIUtils**: Common UI operations
- **DragController**: Drag and drop handling
- **UIScalingManager**: DPI scaling management

### Features
- **Accessibility**: Full keyboard navigation
- **Internationalization**: Multi-language support
- **Performance**: Optimized rendering
- **Extensibility**: Plugin architecture

## Customization

### Look & Feel
```java
// Apply Aero theme
UIManager.setLookAndFeel(new AeroLookAndFeel());

// Custom scaling
UIScalingManager.setScaleFactor(1.25);

// Theme variant
Theme.setVariant(Theme.Variant.AERO);
```

### Component Styling
- **Color schemes**: Customizable palettes
- **Font scaling**: Size adjustment
- **Animation timing**: Speed configuration
- **Visual effects**: Blur, shadow, glow options

## Performance

### Rendering Optimization
- **Double buffering**: Flicker-free rendering
- **GPU acceleration**: Hardware acceleration
- **Lazy loading**: Component initialization
- **Resource pooling**: Memory efficiency

### Memory Management
- **Weak references**: Automatic cleanup
- **Component recycling**: Reuse patterns
- **Event cleanup**: Prevent memory leaks
- **Resource disposal**: Proper cleanup

## Accessibility

### Features
- **Keyboard navigation**: Full keyboard access
- **Screen reader**: ARIA labels support
- **High contrast**: Accessibility themes
- **Focus management**: Logical tab order

### Standards Compliance
- **WCAG 2.1**: Accessibility guidelines
- **Section 508**: Government compliance
- **Keyboard standards**: Platform consistency

## Usage Examples

### Basic Component Usage
```java
// Create a modern text field
ModernTextField textField = new ModernTextField();
textField.setPlaceholder("Enter text here...");
textField.setValidationMode(ValidationMode.ON_INPUT);

// Create a rounded button
RoundedButton button = new RoundedButton("Click Me");
button.setStyle(ButtonStyle.PRIMARY);
button.setAnimationEnabled(true);

// Create an Aero panel
AeroPanel panel = new AeroPanel();
panel.setBlurRadius(10);
panel.setOpacity(0.8f);
panel.add(textField);
```

### Theme Integration
```java
// Apply Aero theme globally
UIManager.setLookAndFeel(new AeroLookAndFeel());

// Set theme variant
Theme.setVariant(Theme.Variant.AERO);

// Custom component styling
AeroTextField field = new AeroTextField();
field.setThemeVariant(Theme.Variant.LIGHT);
field.setBorderRadius(8);
field.setShadowEnabled(true);
```

### Animation Examples
```java
// Fade transition
FadeTransitionPanel fadePanel = new FadeTransitionPanel();
fadePanel.setFadeInDuration(300);
fadePanel.setFadeOutDuration(200);
fadePanel.showComponent(newComponent);

// Animated popup
AnimatedGlassPopup popup = new AnimatedGlassPopup();
popup.setAnimationDuration(400);
popup.setEasingFunction(EasingFunction.EASE_OUT_CUBIC);
popup.show(content);
```

## Integration Guide

### Maven/Gradle Setup
```xml
<!-- Maven dependency -->
<dependency>
    <groupId>com.simjot</groupId>
    <artifactId>ui-components</artifactId>
    <version>1.0.0</version>
</dependency>
```

```gradle
// Gradle dependency
implementation 'com.simjot:ui-components:1.0.0'
```

### Project Structure
```
src/main/ui/components/
├── buttons/           # Button components
├── containers/        # Panel and container components
├── dialogs/          # Dialog implementations
├── fields/           # Input field components
├── icons/            # Icon management and rendering
├── menus/            # Menu components
├── panels/           # Specialized panels
├── scrollbars/       # Custom scrollbar implementations
├── themes/           # Theme system and look & feel
├── toolbars/         # Toolbar components
├── util/             # Utility classes and helpers
└── README.md         # This documentation
```

## Best Practices

### Performance Tips
- Use `DoubleBufferedPanel` for complex graphics
- Enable GPU acceleration for animations
- Implement proper cleanup in custom components
- Use component pooling for frequently created/destroyed elements

### Accessibility Guidelines
- Always provide keyboard alternatives to mouse actions
- Set proper ARIA labels and descriptions
- Ensure sufficient color contrast ratios
- Test with screen readers

### Theme Development
- Extend `Theme` class for custom themes
- Implement `ThemeVariant` enum for new variants
- Use `ThemeManager` for dynamic theme switching
- Test across all supported variants

## API Reference

### Core Interfaces
- `ThemedComponent`: Interface for theme-aware components
- `Animatable`: Interface for animation-capable components
- `ScalableComponent`: Interface for DPI-aware components

### Base Classes
- `ModernComponent`: Base class for all modern components
- `AnimatedComponent`: Base class for animated components
- `ThemedPanel`: Base class for themed panels

## Contributing

When contributing to this component library:
1. Follow the established naming conventions
2. Ensure accessibility compliance
3. Add comprehensive Javadoc documentation
4. Include unit tests for new functionality
5. Test across all theme variants
6. Verify performance impact

## License

These UI components are provided under a custom proprietary license that allows free use in Java Swing projects. See the `LICENSE.md` file in this directory for complete terms and conditions.

## Support

For questions, bug reports, or feature requests:
- Check the API documentation
- Review the example code
- Submit issues through the project repository
- Contact the development team
