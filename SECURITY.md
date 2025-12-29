# SECURITY.md

## Security Policy

This document outlines Simjot's security practices and vulnerability reporting procedures.

## Supported Versions

| Version | Supported | Security Updates |
|---------|-----------|------------------|
| 1.0.0   | Yes       | Yes              |

## Security Features

### Data Protection
- **AES-256 Encryption** - All journal content is encrypted when password protection is enabled
- **Local Storage** - All data is stored locally on user's device; no cloud storage or data transmission
- **Memory Management** - Sensitive data is cleared from memory when no longer needed
- **Secure Backup** - Backup files maintain encryption and integrity verification

### Access Control
- **Password Protection** - Optional password-based access control
- **Auto-Lock** - Configurable inactivity timeout with automatic locking
- **Session Management** - Secure session handling with proper cleanup

### Network Security
- **No Telemetry** - No data collection or telemetry by default

## Vulnerability Reporting

### Reporting Process
If you discover a security vulnerability, please report it responsibly:

1. **Do** open a public issue
2. **Include**:
   - Detailed description of the vulnerability
   - Steps to reproduce
   - Potential impact assessment
   - Any proof-of-concept code (if applicable)

### Response Timeline
- **Initial Response**: Within 48 hours
- **Assessment**: Within 7 days
- **Patch Release**: Based on severity assessment
- **Public Disclosure**: After patch is available

### Severity Classification
- **Critical**: Immediate system compromise, data exposure
- **High**: Significant impact on security/privacy
- **Medium**: Limited impact, requires specific conditions
- **Low**: Minor security issue, minimal impact

## Security Best Practices

### For Developers
1. **Input Validation** - Validate all user inputs
2. **Error Handling** - Don't expose sensitive information in error messages
3. **Dependency Management** - Keep dependencies updated and reviewed
4. **Code Review** - Security-focused code review process
5. **Testing** - Include security testing in development workflow

## Known Security Considerations

### Local File Access
- Simjot requires file system access for journal storage
- Users should ensure proper file permissions on journal directories
- Backup files inherit the same security level as original data

### Memory Management
- Sensitive data is retained in memory during active sessions
- Memory is cleared on application exit and lock events
- Users should lock the application when away from their computer

## Security Updates

### Update Process
- Security updates are released as part of regular version updates
- Critical vulnerabilities may receive out-of-band patches
- Users are encouraged to enable automatic update checking

### Verification
- All releases are digitally signed
- Checksums are provided for download verification
- Update integrity is verified during installation

### General Inquiries
- **GitHub Issues**: For non-security related bugs
- **Documentation**: See `CONTRIBUTING.md` for development guidelines

## Security Acknowledgments

We thank security researchers and users who help improve Simjot's security through responsible vulnerability reporting.

---

**Last Updated**: December 2024  
**Version**: 1.0.0  
**Policy Review**: Quarterly
