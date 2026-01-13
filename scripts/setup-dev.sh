#!/bin/bash
# Setup script for development environment
# Run: ./scripts/setup-dev.sh

set -e

echo "🚀 Setting up Termux Kotlin development environment..."

# Check Java version
echo "☕ Checking Java version..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo "❌ Java 17+ is required. Found Java $JAVA_VERSION"
        exit 1
    fi
    echo "✅ Java $JAVA_VERSION detected"
else
    echo "❌ Java not found. Please install JDK 17+"
    exit 1
fi

# Make gradlew executable
echo "🔧 Setting up Gradle..."
chmod +x gradlew

# Install pre-commit hook
echo "🪝 Installing pre-commit hook..."
if [ -d ".git" ]; then
    mkdir -p .git/hooks
    cp scripts/pre-commit .git/hooks/pre-commit
    chmod +x .git/hooks/pre-commit
    echo "✅ Pre-commit hook installed"
else
    echo "⚠️  Not a git repository, skipping hook installation"
fi

# Download dependencies
echo "📦 Downloading dependencies..."
./gradlew dependencies --quiet

# Verify build
echo "🔨 Verifying build..."
./gradlew assembleDebug --quiet

echo ""
echo "✅ Development environment setup complete!"
echo ""
echo "Next steps:"
echo "  1. Open project in Android Studio"
echo "  2. Run './gradlew check' to verify all checks pass"
echo "  3. Start developing! 🎉"
