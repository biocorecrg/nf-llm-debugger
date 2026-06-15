package nextflow.debugger

import java.nio.file.Path
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.processor.TaskHandler
import nextflow.trace.TraceRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage

class DebuggerObserver implements TraceObserver {
    private static final Logger log = LoggerFactory.getLogger(DebuggerObserver)
    private Session session

    DebuggerObserver(Session session) {
        this.session = session
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        if (handler == null) {
            log.debug("🤖 [nf-llm-debugger] onFlowError called with null TaskHandler")
            return
        }
        def task = handler.getTask()
        if (task == null) {
            log.debug("🤖 [nf-llm-debugger] onFlowError TaskHandler returned null task")
            return
        }
        def errorReport = "Task ${task.name} [${task.id}] failed.\nWork Directory: ${task.workDir}\n\nError report:\n${task.errorReport ?: 'No error report available.'}"
        llmExplainError(errorReport)
    }

    @Override
    void onFlowComplete() {
        log.info("🤖 [nf-llm-debugger] onFlowComplete - Success: ${session.isSuccess()}")
        if (!session.isSuccess()) {
            def err = session.getError()
            log.info("🤖 [nf-llm-debugger] onFlowComplete - Error: ${err}")
            def errorReport = err ? (err.getMessage() ?: err.toString()) : "Workflow failed (no exception details available in session)."
            llmExplainError(errorReport)
        }
    }

    private void llmExplainError(String report) {
        def params = session.binding.getVariable('params') as Map ?: [:]
        
        // Resolve parameters with safe defaults
        def endpoint = params.containsKey('llm_endpoint') ? params.llm_endpoint?.toString()?.trim() : null
        // Backward compatibility fallback to llm_address
        if (!endpoint && params.containsKey('llm_address')) {
            endpoint = params.llm_address?.toString()?.trim()
        }
        
        def model = params.containsKey('llm_model') ? params.llm_model?.toString() : null
        def apiKey = params.containsKey('llm_api_key') ? params.llm_api_key?.toString() : ''
        def answerStyle = params.containsKey('llm_answer') ? params.llm_answer?.toString()?.toLowerCase()?.trim() : 'standard'

        // Default if not specified
        if (!endpoint) {
            endpoint = 'local'
        }

        // Determine provider and custom address from endpoint
        def provider = 'local'
        def address = null

        def lowerEndpoint = endpoint.toLowerCase()
        if (lowerEndpoint == 'gemini') {
            provider = 'gemini'
        } else if (lowerEndpoint == 'claude' || lowerEndpoint == 'anthropic') {
            provider = 'claude'
        } else if (lowerEndpoint == 'chatgpt' || lowerEndpoint == 'openai') {
            provider = 'openai'
        } else if (lowerEndpoint == 'ollama') {
            provider = 'ollama'
        } else if (lowerEndpoint == 'local' || lowerEndpoint == 'llamafile') {
            provider = 'local'
        } else {
            // It's a custom URL!
            address = endpoint
            // Auto-detect provider based on URL pattern or model name
            def lookupStr = address.toLowerCase() + " " + (model ?: "").toLowerCase()
            if (lookupStr.contains("gemini") || lookupStr.contains("googleapis.com")) {
                provider = 'gemini'
            } else if (lookupStr.contains("claude") || lookupStr.contains("anthropic.com")) {
                provider = 'claude'
            } else if (lookupStr.contains("openai.com") || (model && model.startsWith("gpt-"))) {
                provider = 'openai'
            } else if (lookupStr.contains("ollama") || lookupStr.contains(":11434")) {
                provider = 'ollama'
            } else {
                provider = 'local'
            }
        }

        ChatLanguageModel chatModel = null
        log.info("🤖 [nf-llm-debugger] Initializing LLM using provider: ${provider}")

        try {
            switch (provider) {
                case 'gemini':
                    if (!apiKey) {
                        apiKey = System.getenv("GEMINI_API_KEY") ?: System.getenv("LLM_API_KEY")
                    }
                    def geminiModel = model ?: 'gemini-1.5-flash'
                    log.info("🤖 [nf-llm-debugger] Configured Gemini Model: ${geminiModel}")
                    chatModel = GoogleAiGeminiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(geminiModel)
                        .temperature(0.2)
                        .build()
                    break

                case 'claude':
                case 'anthropic':
                    if (!apiKey) {
                        apiKey = System.getenv("ANTHROPIC_API_KEY") ?: System.getenv("LLM_API_KEY")
                    }
                    def anthropicModel = model ?: 'claude-3-5-sonnet-20241022'
                    log.info("🤖 [nf-llm-debugger] Configured Claude Model: ${anthropicModel}")
                    chatModel = AnthropicChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(anthropicModel)
                        .temperature(0.2)
                        .build()
                    break

                case 'openai':
                case 'chatgpt':
                    if (!apiKey) {
                        apiKey = System.getenv("OPENAI_API_KEY") ?: System.getenv("LLM_API_KEY")
                    }
                    def openAiModel = model ?: 'gpt-4o-mini'
                    log.info("🤖 [nf-llm-debugger] Configured ChatGPT Model: ${openAiModel}")
                    chatModel = OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(openAiModel)
                        .temperature(0.2)
                        .build()
                    break

                case 'ollama':
                    def ollamaAddress = address ?: 'http://localhost:11434'
                    def ollamaModel = model ?: 'llama3'
                    log.info("🤖 [nf-llm-debugger] Configured Ollama Model: ${ollamaModel} at ${ollamaAddress}")
                    chatModel = OllamaChatModel.builder()
                        .baseUrl(ollamaAddress)
                        .modelName(ollamaModel)
                        .temperature(0.2)
                        .build()
                    break

                case 'local':
                case 'llamafile':
                default:
                    def resolvedAddress = address ?: 'http://localhost:8080/v1'
                    if (resolvedAddress.endsWith("/chat/completions")) {
                        resolvedAddress = resolvedAddress.substring(0, resolvedAddress.length() - "/chat/completions".length())
                    }
                    if (resolvedAddress.endsWith("/")) {
                        resolvedAddress = resolvedAddress.substring(0, resolvedAddress.length() - 1)
                    }
                    if (!apiKey) {
                        apiKey = System.getenv("LLM_API_KEY") ?: System.getenv("OPENAI_API_KEY") ?: 'ignored'
                    }
                    def localModel = model ?: 'LLaMA_CPP'
                    log.info("🤖 [nf-llm-debugger] Configured Local/Llamafile Model: ${localModel} at ${resolvedAddress}")
                    chatModel = OpenAiChatModel.builder()
                        .baseUrl(resolvedAddress)
                        .modelName(localModel)
                        .apiKey(apiKey)
                        .temperature(0.2)
                        .build()
                    break
            }

            if (!chatModel) {
                throw new IllegalStateException("Failed to configure LLM chat model for provider: ${provider}")
            }

            // Resolve custom documentation parameter if configured
            def docsPath = params.containsKey('llm_docs') && params.llm_docs ? params.llm_docs.toString() : null
            def docsText = ""
            if (docsPath) {
                def docFile = new File(docsPath)
                if (docFile.exists()) {
                    docsText = "\n\nAdditional pipeline documentation for reference:\n" + docFile.text
                } else {
                    log.warn("[nf-llm-debugger] Documentation file not found: ${docsPath}")
                }
            }

            // Construct system instruction
            def systemPrompt = ""
            if (answerStyle == 'concise') {
                systemPrompt = "You are an expert bioinformatics pipeline debugger. Analyze the Nextflow pipeline error report and explain the cause and the fix in one or two sentences. Do not write a long explanation or list of suggestions."
            } else if (answerStyle == 'extense' || answerStyle == 'extensive') {
                systemPrompt = "You are an expert bioinformatics pipeline debugger. Analyze the Nextflow pipeline error report, explain what went wrong in clear, understandable terms, and suggest specific actionable fixes. Please provide a highly detailed and extensive explanation."
            } else {
                systemPrompt = "You are an expert bioinformatics pipeline debugger. Analyze the Nextflow pipeline error report, explain what went wrong in clear, understandable terms, and suggest specific actionable fixes."
            }
            systemPrompt += docsText

            log.info("🤖 [nf-llm-debugger] Generating LLM diagnosis...")
            def response = chatModel.generate([
                new SystemMessage(systemPrompt),
                new UserMessage(report)
            ])

            def explanation = response.content()?.text()
            if (explanation) {
                log.info("\n" + "=" * 80)
                log.info("🤖 [nf-llm-debugger] LLM ERROR DIAGNOSIS:")
                log.info("=" * 80)
                log.info(explanation)
                log.info("=" * 80 + "\n")
            } else {
                log.warn("[nf-llm-debugger] LLM returned an empty response.")
            }
        }
        catch (e) {
            log.warn("[nf-llm-debugger] Error running LLM diagnosis: ${e.message}", e)
        }
    }
}
