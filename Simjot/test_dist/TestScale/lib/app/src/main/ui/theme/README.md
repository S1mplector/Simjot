# Theming System

The theming engine provides comprehensive visual customization for Simjot with Aero-inspired design, multiple theme variants, and advanced theming capabilities.

## Theme Architecture

### Core Theme Classes
- **Theme**: Central theme management and variant selection
- **AeroTheme**: Windows 7 Aero glass implementation
- **AeroLookAndFeel**: Complete Look and Feel override
- **UIScalingManager**: DPI-aware scaling and density management

### Theme Variants
```java
public enum Variant {
    AERO,    // Windows 7 Aero glass with blur effects
    LIGHT,   // Clean, modern light theme
    SEPIA    // Warm, eye-friendly sepia tones
}
```

## Aero Theme Features

### Glass Morphism
- **Blur effects**: Background blur for depth
- **Transparency**: Adjustable opacity levels
- **Reflections**: Subtle surface reflections
- **Gradients**: Smooth color transitions

### Visual Effects
- **Glow effects**: Soft edge illumination
- **Shadows**: Realistic depth shadows
- **Animations**: Smooth transitions and micro-interactions
- **Highlights**: Subtle edge highlights

### Color Palette
```java
// Aero primary colors
Color aeroBlue = new Color(0, 122, 204);      // Primary accent
Color aeroGlass = new Color(255, 255, 255, 180); // Glass white
Color aeroDark = new Color(30, 30, 30, 200);   // Dark overlay
Color aeroBorder = new Color(200, 200, 200, 100); // Subtle borders
```

## Theme Implementation

### Look and Feel Override
```java
public class AeroLookAndFeel extends LookAndFeel {
    // Custom UI delegates for all components
    // Aero-specific painting and behavior
    // Integration with system theming
}
```

### Component Theming
- **AeroPanelUI**: Glass panel rendering
- **AeroButtonUI**: Translucent button styling
- **AeroScrollBarUI**: Custom scrollbar appearance
- **AeroTextFieldUI**: Glass text field styling

### Custom UI Delegates
- **ModernScrollBarUI**: Clean scrollbar design
- **ModernComboBoxUI**: Dropdown styling
- **ModernCheckBoxUI**: Checkbox appearance
- **ModernSpinnerUI**: Numeric input styling

## Scaling System

### DPI Awareness
```java
public enum Density {
    COMPACT,    // 0.85x scale for high DPI
    NORMAL,     // 1.0x standard scale
    COMFORTABLE, // 1.25x for readability
    SPACIOUS    // 1.5x for accessibility
}
```

### Scaling Features
- **Automatic detection** of system DPI settings
- **Manual override** for user preference
- **Component-aware scaling** for proper layout
- **Font scaling** with readability optimization

### Density Management
```java
// Set scaling density
Theme.setDensity(Density.COMFORTABLE);

// Get current scale factor
double scale = Theme.getScaleFactor();

// Apply scaling to component
component.setFont(Theme.getScaledFont(baseFont));
```

## Theme Customization

### Color Schemes
- **Primary colors**: Main accent colors
- **Secondary colors**: Supporting palette
- **Surface colors**: Background and panel colors
- **Text colors**: Readable text hierarchy

### Typography
- **Font families**: System and custom fonts
- **Font weights**: Light, regular, bold variants
- **Font sizes**: Scalable size system
- **Line height**: Readable spacing

### Visual Effects
- **Blur radius**: Adjustable glass blur
- **Opacity levels**: Transparency control
- **Shadow depth**: 3D shadow intensity
- **Animation timing**: Motion speed control

## Theme Switching

### Dynamic Switching
```java
// Switch theme variant
Theme.setVariant(Theme.Variant.LIGHT);

// Apply to entire application
SwingUtilities.updateComponentTreeUI(frame);

// Persist user preference
SettingsStore.get().setTheme("Light");
```

### Transition Effects
- **Fade transitions** between themes
- **Color interpolation** for smooth changes
- **Component animations** during switch
- **State preservation** during transitions

## Performance

### Rendering Optimization
- **Hardware acceleration** for glass effects
- **Cached graphics** for repeated elements
- **Lazy rendering** for off-screen components
- **GPU utilization** for blur operations

### Memory Management
- **Image caching** for theme resources
- **Graphics disposal** for memory cleanup
- **Weak references** for temporary objects
- **Resource pooling** for frequent operations

## Accessibility

### High Contrast Support
- **Increased contrast** for visibility
- **Reduced transparency** for clarity
- **Enhanced borders** for definition
- **Larger touch targets** for interaction

### Visual Adjustments
- **Color blindness** friendly palettes
- **Reduced motion** for sensitivity
- **High contrast** themes
- **Large text** options

## Integration

### Component Integration
- **Automatic theming** for standard components
- **Custom component** theming support
- **Third-party component** styling
- **Legacy component** compatibility

### System Integration
- **Native theme** detection and adaptation
- **System color** scheme synchronization
- **Platform-specific** theming rules
- **Dark mode** support (mapped to LIGHT variant)

## Development

### Extending Themes
```java
// Create custom theme variant
public class CustomTheme extends AeroTheme {
    @Override
    protected Color getPrimaryColor() {
        return new Color(100, 150, 200);
    }
    
    @Override
    protected double getBlurRadius() {
        return 15.0; // Increased blur
    }
}
```

### Theme Testing
- **Visual regression** testing
- **Cross-platform** compatibility
- **Accessibility** validation
- **Performance** benchmarking

## Configuration

### Theme Settings
```java
// Theme preferences
SettingsStore.set("theme.variant", "AERO");
SettingsStore.set("theme.density", "COMFORTABLE");
SettingsStore.set("theme.animations", true);
SettingsStore.set("theme.blur", true);
```

### User Preferences
- **Theme selection** with preview
- **Density adjustment** with live preview
- **Animation controls** for performance
- **Accessibility options** for needs
