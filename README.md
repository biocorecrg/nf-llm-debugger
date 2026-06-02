# 🤖 nf-llm-debugger

A Nextflow plugin that automatically intercepts pipeline and process failures at runtime, diagnoses them using local or remote Large Language Models (LLMs), and outputs clear, actionable solutions directly to your console and Nextflow logs!

---

## ✨ Features

- **Runtime Error Interception**: Automatically hooks into the Nextflow JVM lifecycle (`TraceObserver`) to catch failures in processes or workflows in real time.
- **Purely Generic & OpenAI-Compatible**: Communicates with any OpenAI-compatible API endpoint (such as **Llamafile**, **Ollama**, **LocalAI**, **OpenAI**, etc.).
- **Zero-Touch Local Analysis**: Seamless out-of-the-box support for offline local LLMs running on your machine.
- **Actionable Debugging Steps**: Translates cryptic log outputs and exit codes (such as exit status `126`, `127`, `1`, etc.) into easy-to-read, structured diagnostics.

---

## 🚀 Quick Start

### 1. Enable the Plugin

Add the plugin declaration to the `plugins` block inside your `nextflow.config`:

```groovy
plugins {
    id 'nf-llm-debugger@1.0.0'
}
```

### 2. Configure Your LLM Endpoint

Configure your LLM server parameters inside your `nextflow.config` (in the `params` block) or pass them in a parameters file (e.g., `params.yaml`):

```yaml
# Example: Local Llamafile / Ollama Server (No API Key required)
llm_address: "http://127.0.0.1:8080/v1/chat/completions"
llm_model: "LLaMA_CPP"
```

If you use a remote OpenAI-compatible service requiring an API key, export it in your shell environment:

```bash
export LLM_API_KEY="your-api-key-here"
```
*(The plugin will automatically detect and resolve `LLM_API_KEY`, `GEMINI_API_KEY`, or `OPENAI_API_KEY` directly from the environment).*

---

## 🛠️ Configuration Parameters

| Parameter | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `params.llm_address` | String | `"http://localhost:8080/v1/chat/completions"` | The URL endpoint of the OpenAI-compatible API. |
| `params.llm_model` | String | `"LLaMA_CPP"` | The model identifier to send in the request payload. |
| `params.llm_api_key` | String | `""` | The API Key. If left empty, the plugin looks up standard env variables. |

---


## 📄 License

Dveloped by **Luca Cozzuto**. Licensed under the [MIT License](LICENSE).
