# UI Components Architecture

The UI components system provides a modern and customizable interface framework for Simjot with Aero-inspired design and theming capabilities.

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
