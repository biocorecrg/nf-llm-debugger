package nextflow.debugger

import java.nio.file.Path
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import org.pf4j.Extension

@Extension
class DebuggerFactory implements TraceObserverFactory {
    @Override
    Collection<TraceObserver> create(Session session) {
        def config = session.config.llm as Map ?: [:]
        def enabled = config.containsKey('enabled') ? config.enabled : false
        def params = session.binding.getVariable('params') as Map ?: [:]
        if (params.containsKey('llm_enabled')) {
            enabled = params.llm_enabled
        }
        if (enabled == false || enabled?.toString()?.toLowerCase() == 'false') {
            return []
        }
        return [new DebuggerObserver(session)]
    }
}
