#!/bin/bash
#
# Install the nf-llm-debugger plugin for local testing
#

set -e

PLUGIN_ID="nf-llm-debugger"
PLUGIN_VERSION="1.0.6"
PLUGIN_DIR="$HOME/.nextflow/plugins/${PLUGIN_ID}-${PLUGIN_VERSION}"
ZIP_FILE="build/distributions/${PLUGIN_ID}-${PLUGIN_VERSION}.zip"

echo "Building plugin..."
./gradlew clean build

# Check if zip exists
if [ ! -f "$ZIP_FILE" ]; then
    echo "Error: zip file not found: $ZIP_FILE"
    exit 1
fi

# Clean up any existing installation
if [ -d "$PLUGIN_DIR" ]; then
    echo "Removing existing plugin installation..."
    rm -rf "$PLUGIN_DIR"
fi

# Create plugin directory structure
mkdir -p "$PLUGIN_DIR"

# Unzip zip file into plugin directory
echo "Installing plugin to: $PLUGIN_DIR"
unzip -q -d "$PLUGIN_DIR" "$ZIP_FILE"

echo ""
echo "✓ Plugin installed successfully to: $PLUGIN_DIR"
echo ""
