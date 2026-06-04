#!/bin/bash
#
# Run local integration validation test
#

set -e

# Make sure we are in the repo root
cd "$(dirname "$0")/.."

echo "===================================================="
echo "Step 1: Installing plugin locally..."
echo "===================================================="
./install-plugin.sh

echo "===================================================="
echo "Step 2: Starting Mock LLM server..."
echo "===================================================="
python3 validation/mock_llm.py &
MOCK_PID=$!

# Ensure mock server is stopped on exit
cleanup() {
    echo "Stopping Mock LLM server (PID: $MOCK_PID)..."
    kill $MOCK_PID || true
}
trap cleanup EXIT

# Wait a moment for server to start
sleep 2

echo "===================================================="
echo "Step 3: Running failing Nextflow pipeline..."
echo "===================================================="
# We capture output but let nextflow fail (since test_failure.nf fails on purpose)
set +e
nextflow run validation/test_failure.nf -c validation/nextflow.config > validation_run.log 2>&1
NF_EXIT=$?
set -e

# Print nextflow output log for visibility
cat validation_run.log

echo "===================================================="
echo "Step 4: Verifying LLM Debugger Output..."
echo "===================================================="
if grep -q "MOCK DIAGNOSIS" validation_run.log; then
    echo ""
    echo "🎉 SUCCESS: The LLM Debugger intercepted the error and successfully logged the mock diagnosis!"
    echo ""
    exit 0
else
    echo ""
    echo "❌ FAILURE: Mock diagnosis not found in the Nextflow log output."
    echo ""
    exit 1
fi
