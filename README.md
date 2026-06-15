# nf-llm-debugger

A Nextflow plugin that automatically intercepts pipeline and process failures at runtime, diagnoses them using local or remote Large Language Models (LLMs), and outputs clear, actionable solutions directly to your console and Nextflow logs.

## Summary

The `nf-llm-debugger` plugin hooks directly into the Nextflow runtime engine to capture process execution failures (such as non-zero exit codes, out-of-memory events, or command errors). It retrieves the failed task context and log outputs, compiles them into a structured report, and queries an OpenAI-compatible Large Language Model (LLM) API (such as Llamafile, Ollama, OpenAI, or LocalAI) to generate an instantaneous, highly accurate error diagnosis.

Key Features:
- **Automatic Runtime Interception**: Monitors the task execution lifecycle (`TraceObserver`) and automatically analyzes errors.
- **Universal Compatibility**: Works with any local or remote OpenAI-compatible completion endpoint (`/v1/chat/completions`).
- **No-Key Local Defaults**: Configured out-of-the-box to use local, offline LLMs running via Llamafile or Ollama.
- **Actionable Diagnoses**: Translates raw stack traces and OS exit codes (like `126`, `127`, `137`) into human-readable steps.

## Get Started

### 1. Requirements

- Nextflow 24.04.0 or newer
- Java 17 or newer

### 2. Enable the Plugin

Add the plugin declaration to the `plugins` block inside your `nextflow.config`:

```groovy
plugins {
    id 'nf-llm-debugger@1.0.7'
}
```

### 3. Configure Your LLM Endpoint and Context

Define your API parameters inside the `params` block of your `nextflow.config`. You can specify a preset provider or configure a custom API URL using a single parameter:

```groovy
params {
    // Can be a preset: 'gemini', 'claude', 'chatgpt', 'ollama', 'local'
    // Or a custom URL: e.g., 'http://localhost:8080/v1' (auto-detects provider based on URL/model)
    llm_endpoint = 'chatgpt' 

    // Optional: Only needed to customize the model or troubleshooting docs
    llm_model = 'gpt-4o-mini' 
    llm_docs = 'nf-debugger-docs.md' 
}
```

Example for Gemini preset:

```groovy
params {
    llm_endpoint = 'gemini'
    // Optional: customize model (defaults to gemini-1.5-flash)
    llm_model = 'gemini-2.5-flash-lite'
    llm_docs = 'nf-debugger-docs.md'                          
}
```

If your LLM endpoint requires authentication, export the corresponding API key in your shell environment:

*   **Gemini**: `export GEMINI_API_KEY="your-gemini-key"`
*   **Claude/Anthropic**: `export ANTHROPIC_API_KEY="your-anthropic-key"`
*   **ChatGPT/OpenAI**: `export OPENAI_API_KEY="your-openai-key"`
*   **Other/Local**: `export LLM_API_KEY="your-api-key"`

## Examples

### 1. Deliberately Failing Pipeline

Below is a simple Nextflow pipeline (`main.nf`) designed to trigger an error by attempting to read a non-existent file:

```groovy
nextflow.enable.dsl=2

process failProcess {
    script:
    """
    echo "Starting a process that will fail..."
    cat nonexistent_file.txt
    """
}

workflow {
    failProcess()
}
```

### 2. Expected Output with LLM Diagnosis

When you run the pipeline above with `nf-llm-debugger` enabled:

```bash
nextflow run main.nf
```

The plugin automatically intercepts the task failure, sends the context to the configured LLM, and prints a structured diagnosis directly to the console:

```text
executor >  local (1)
[03/4f4d68] failProcess | 0 of 1 ✘
🤖 [nf-llm-debugger] onFlowComplete - Success: false
🤖 [nf-llm-debugger] onFlowComplete - Error: Process `failProcess` terminated with an error exit status (1)
🤖 [nf-llm-debugger] Sending error report to LLM (model: LLaMA_CPP)...

================================================================================
🤖 [nf-llm-debugger] LLM ERROR DIAGNOSIS:
================================================================================
🤖 [DIAGNOSIS] The task 'failProcess' failed because the command tried to execute
'cat nonexistent_file.txt', but the file 'nonexistent_file.txt' does not exist
in the task's working directory.

💡 SUGGESTIONS:
1. Double-check if the file name is spelled correctly in your process script.
2. If this file is an input, make sure you declared it correctly in the `input:`
   block of the process so Nextflow stages it into the work directory.
================================================================================

ERROR ~ Error executing process > 'failProcess'

Caused by:
  Process `failProcess` terminated with an error exit status (1)
```

## Configuration

| Parameter | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `params.llm_endpoint` | String | `"local"` | The target LLM destination. Can be a preset (`"gemini"`, `"claude"`, `"chatgpt"`, `"ollama"`, `"local"`) or a custom API endpoint URL. |
| `params.llm_model` | String | (depends on provider) | The model identifier (e.g. `gpt-4o-mini`, `gemini-1.5-flash`, `claude-3-5-sonnet-20241022`). |
| `params.llm_api_key` | String | `""` | The API Key. If left empty, the plugin looks up provider-specific env variables. |
| `params.llm_answer` | String | `"standard"` | Controls the verbosity of the LLM diagnosis. Accepts `"concise"`, `"extense"`, or `"standard"`. |
| `params.llm_docs` | String | `""` | Optional file path to custom markdown pipeline/tool documentation. If set and the file exists, it is appended to the system prompt to guide LLM debugging. |

You can supply additional pipeline/tool-specific troubleshooting documentation to improve the quality of the LLM diagnoses.
If `params.llm_docs` is configured (e.g., set to `'nf-debugger-docs.md'`), the plugin searches for that file path relative to the Nextflow execution directory (or absolute path if provided) and appends its contents to the LLM system prompt context.

## 📄 License

Developed by **CRG** and **Luca Cozzuto**. Licensed under the [MIT License](LICENSE).
