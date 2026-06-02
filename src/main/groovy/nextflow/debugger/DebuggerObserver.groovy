package nextflow.debugger

import java.nio.file.Path
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.processor.TaskHandler
import nextflow.trace.TraceRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import groovy.json.JsonSlurper

class DebuggerObserver implements TraceObserver {
    private static final Logger log = LoggerFactory.getLogger(DebuggerObserver)
    private Session session

    DebuggerObserver(Session session) {
        this.session = session
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        def task = handler.getTask()
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
        def defaultAddress = params.containsKey('llamafile_address') ? params.llamafile_address : 'http://localhost:8080/v1/chat/completions'
        def resolvedEndpoint = params.containsKey('llm_address') ? params.llm_address : defaultAddress
        def model = params.containsKey('llm_model') ? params.llm_model : 'LLaMA_CPP'
        def apiKey = params.containsKey('llm_api_key') ? params.llm_api_key : ''

        // Resolve API key from environment if not explicitly set in config
        if (!apiKey) {
            apiKey = System.getenv("LLM_API_KEY") ?: (System.getenv("GEMINI_API_KEY") ?: System.getenv("OPENAI_API_KEY"))
        }

        log.info("🤖 [nf-llm-debugger] Sending error report to LLM (model: ${model})...")

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
        def systemPrompt = "You are an expert bioinformatics pipeline debugger. Analyze the Nextflow pipeline error report, explain what went wrong in clear, understandable terms, and suggest specific actionable fixes." + docsText

        // Create a temporary JSON payload file to safely handle quotes and newlines
        def myFile = File.createTempFile('llm_payload', '.json')

        // Safely escape quotes and newlines for valid JSON
        def escapedSystem = systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
        def escapedUser = report.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

        myFile.text = """{
  "model": "${model}",
  "messages": [
    {"role": "system", "content": "${escapedSystem}"},
    {"role": "user", "content": "${escapedUser}"}
  ],
  "temperature": 0.2
}"""

        // Construct curl command
        def cmd = ["curl", "--connect-timeout", "15", "-s", "-X", "POST", "-H", "Content-Type: application/json"]
        if (apiKey) {
            cmd << "-H"
            cmd << "Authorization: Bearer ${apiKey}"
        }
        cmd << "-d"
        cmd << "@${myFile.absolutePath}"
        cmd << resolvedEndpoint

        def explanation = null
        try {
            def proc = cmd.execute()
            proc.waitFor()

            if (proc.exitValue() == 0) {
                def responseText = proc.text
                def slurper = new JsonSlurper()
                def json = slurper.parseText(responseText)
                
                if (json instanceof Map) {
                    if (json.containsKey('choices') && json.choices.size() > 0) {
                        explanation = json.choices[0].message.content
                    } else if (json.containsKey('error')) {
                        log.warn("[nf-llm-debugger] LLM API returned error: ${json.error.message ?: json.error}")
                    } else {
                        log.warn("[nf-llm-debugger] Unexpected LLM response format: ${responseText}")
                    }
                } else {
                    log.warn("[nf-llm-debugger] Unexpected LLM response (not a JSON Map): ${responseText}")
                }

                if (explanation) {
                    log.info("\n" + "=" * 80)
                    log.info("🤖 [nf-llm-debugger] LLM ERROR DIAGNOSIS:")
                    log.info("=" * 80)
                    log.info(explanation)
                    log.info("=" * 80 + "\n")
                }
            } else {
                log.warn("[nf-llm-debugger] Could not connect to LLM server at ${resolvedEndpoint} (Exit code: ${proc.exitValue()}).")
            }
        }
        catch (e) {
            log.warn("[nf-llm-debugger] Error running LLM diagnosis: ${e.message}")
        }

        myFile.delete()
    }
}
