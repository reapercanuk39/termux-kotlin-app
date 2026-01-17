# Termux-Kotlin Package Repository

Custom-built Termux packages with native `com.termux.kotlin` paths.

## Packages Available

| Package | Version | Description |
|---------|---------|-------------|
| apt | 2.8.1-2 | APT package manager |
| dpkg | 1.22.6-5 | Debian package manager |
| termux-exec | 1:2.4.0-1 | LD_PRELOAD shebang fix |
| termux-tools | 1.46.0+really1.45.0-1 | Core termux utilities |
| termux-core | 0.4.0-1 | Core termux libraries |
| termux-api | 0.59.1-1 | Terminal API client |
| termux-am | 0.8.0-2 | Activity manager |
| termux-keyring | 3.13 | GPG keyring |
| termux-licenses | 2.1 | License files |
| libgnutls | 3.8.11 | GnuTLS SSL/TLS library |
| libcurl | 8.18.0 | URL transfer library |
| libgpg-error | 1.58 | GPG error handling library |

## Architectures

- aarch64 (ARM64)
- arm (ARMv7)
- x86_64 (Intel/AMD 64-bit)
- i686 (Intel/AMD 32-bit)
- all (Architecture-independent)

## Usage

To use this repository, add to `/data/data/com.termux.kotlin/files/usr/etc/apt/sources.list`:

```
deb [trusted=yes] https://reapercanuk39.github.io/termux-kotlin-app/repo/ termux-kotlin main
```

## Build Information

All packages built with:
- `TERMUX_APP__PACKAGE_NAME="com.termux.kotlin"`
- Native paths: `/data/data/com.termux.kotlin/files/usr`

No runtime path patching required - paths are compiled directly into binaries.
