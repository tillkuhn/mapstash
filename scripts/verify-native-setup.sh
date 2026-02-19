#!/bin/bash
# Verification script for GraalVM native image setup

echo "🔍 Verifying GraalVM Native Image Configuration"
echo "================================================"
echo ""

# Check if GraalVM is installed
echo "1. Checking GraalVM installation..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    echo "   ✅ Java found: $JAVA_VERSION"

    if [[ $JAVA_VERSION == *"GraalVM"* ]]; then
        echo "   ✅ GraalVM detected"
    else
        echo "   ⚠️  Not using GraalVM (native image build will fail)"
        echo "   💡 Install GraalVM: sdk install java 25.0.2-graalce"
    fi
else
    echo "   ❌ Java not found"
    exit 1
fi
echo ""

# Check if native-image tool is available
echo "2. Checking native-image tool..."
if command -v native-image &> /dev/null; then
    echo "   ✅ native-image tool found"
else
    echo "   ⚠️  native-image tool not found"
    echo "   💡 Install: gu install native-image"
fi
echo ""

# Verify Maven configuration
echo "3. Checking Maven configuration..."
if [ -f "pom.xml" ]; then
    echo "   ✅ pom.xml found"

    if grep -q "native-maven-plugin" pom.xml; then
        echo "   ✅ native-maven-plugin configured"
    else
        echo "   ❌ native-maven-plugin not found in pom.xml"
    fi
else
    echo "   ❌ pom.xml not found"
    exit 1
fi
echo ""

# Check native image configuration files
echo "4. Checking native image configuration files..."
CONFIG_DIR="src/main/resources/META-INF/native-image/com.mapstash/mapstash"
if [ -d "$CONFIG_DIR" ]; then
    echo "   ✅ Configuration directory exists"

    if [ -f "$CONFIG_DIR/reflect-config.json" ]; then
        echo "   ✅ reflect-config.json found"
    else
        echo "   ⚠️  reflect-config.json not found"
    fi

    if [ -f "$CONFIG_DIR/resource-config.json" ]; then
        echo "   ✅ resource-config.json found"
    else
        echo "   ⚠️  resource-config.json not found"
    fi
else
    echo "   ⚠️  Configuration directory not found"
fi
echo ""

# Check Makefile targets
echo "5. Checking Makefile targets..."
if [ -f "Makefile" ]; then
    echo "   ✅ Makefile found"

    if grep -q "^native:" Makefile; then
        echo "   ✅ 'make native' target available"
    else
        echo "   ❌ 'make native' target not found"
    fi

    if grep -q "^native-test:" Makefile; then
        echo "   ✅ 'make native-test' target available"
    else
        echo "   ❌ 'make native-test' target not found"
    fi
else
    echo "   ❌ Makefile not found"
fi
echo ""

echo "================================================"
echo "✅ Setup verification complete!"
echo ""
echo "To build a native image, run:"
echo "   make native"
echo ""
echo "Or with Maven:"
echo "   mvn -Pnative native:compile"
echo ""
