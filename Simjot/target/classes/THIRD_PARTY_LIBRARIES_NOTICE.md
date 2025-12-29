# Third-Party Libraries Notice

## Apache PDFBox

**Version**: 2.0.29  
**License**: Apache License 2.0  
**Website**: https://pdfbox.apache.org/  
**Usage**: PDF export functionality for journal entries and poetry collections

### License Summary
The Apache License 2.0 is a permissive free software license that allows:
- Commercial use
- Distribution
- Modification
- Patent use
- Private use

This license requires preservation of copyright and license notices.

## JNativeHook

**Version**: 2.2.2  
**License**: Apache License 2.0  
**Website**: https://github.com/kwhat/jnativehook  
**Usage**: Global hotkey support for quick capture functionality

### License Summary
The Apache License 2.0 is a permissive free software license that allows:
- Commercial use
- Distribution
- Modification
- Patent use
- Private use

This license requires preservation of copyright and license notices.

## Maven Dependencies

These libraries are managed through Maven and are included in the built JAR file via the maven-shade-plugin.

### Maven Configuration
```xml
<dependencies>
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>2.0.29</version>
    </dependency>
    <dependency>
        <groupId>com.github.kwhat</groupId>
        <artifactId>jnativehook</artifactId>
        <version>2.2.2</version>
    </dependency>
</dependencies>
```

## Compliance

Simjot complies with all licensing requirements for these third-party libraries. All copyright notices are preserved, license files are included in the distribution, no modifications have been made to the library source code and proper attribution is maintained respectfully. 

## For More Information

- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- Apache PDFBox License: https://pdfbox.apache.org/2.0/license.html
- JNativeHook License: https://github.com/kwhat/jnativehook/blob/master/LICENSE

---
*This notice ensures compliance with third-party library licensing requirements.*
