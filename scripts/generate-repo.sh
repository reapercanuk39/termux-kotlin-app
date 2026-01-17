#!/bin/bash
# Generate Termux-Kotlin package repository
# Creates Packages, Packages.gz, and Release files

set -e

REPO_DIR="${1:-$(dirname "$0")/../repo}"
GPG_KEY="${GPG_KEY:-}"  # Optional GPG key for signing

cd "$REPO_DIR"

generate_packages() {
    local arch="$1"
    local arch_dir="$REPO_DIR/$arch"
    
    if [[ ! -d "$arch_dir" ]]; then
        echo "Creating $arch directory..."
        mkdir -p "$arch_dir"
        return
    fi
    
    echo "Generating Packages for $arch..."
    
    cd "$arch_dir"
    
    # Generate Packages file
    > Packages
    
    for deb in *.deb; do
        if [[ -f "$deb" ]]; then
            # Extract control file
            ar p "$deb" control.tar.* 2>/dev/null | tar -xO ./control 2>/dev/null >> Packages || true
            
            # Add filename, size, and checksums
            echo "Filename: $arch/$deb" >> Packages
            echo "Size: $(stat -c%s "$deb")" >> Packages
            echo "SHA256: $(sha256sum "$deb" | cut -d' ' -f1)" >> Packages
            echo "" >> Packages
        fi
    done
    
    # Compress
    gzip -kf Packages
    xz -kf Packages 2>/dev/null || true
    
    cd "$REPO_DIR"
}

generate_release() {
    echo "Generating Release file..."
    
    cat > Release << EOF_RELEASE
Origin: Termux-Kotlin
Label: Termux-Kotlin
Suite: stable
Codename: termux-kotlin
Architectures: aarch64 arm x86_64 i686 all
Components: main
Description: Termux-Kotlin Package Repository
Date: $(date -R)
EOF_RELEASE
    
    # Add checksums for all package files
    echo "SHA256:" >> Release
    for arch in aarch64 arm x86_64 i686 all; do
        for f in "$arch/Packages" "$arch/Packages.gz" "$arch/Packages.xz"; do
            if [[ -f "$f" ]]; then
                echo " $(sha256sum "$f" | cut -d' ' -f1) $(stat -c%s "$f") $f" >> Release
            fi
        done
    done
    
    # Sign if GPG key is available
    if [[ -n "$GPG_KEY" ]]; then
        echo "Signing Release..."
        gpg --default-key "$GPG_KEY" --armor --detach-sign -o Release.gpg Release
        gpg --default-key "$GPG_KEY" --clearsign -o InRelease Release
    fi
}

# Main
echo "=== Termux-Kotlin Repository Generator ==="
echo "Repository: $REPO_DIR"

for arch in aarch64 arm x86_64 i686 all; do
    generate_packages "$arch"
done

generate_release

echo ""
echo "Repository generated successfully!"
ls -la
