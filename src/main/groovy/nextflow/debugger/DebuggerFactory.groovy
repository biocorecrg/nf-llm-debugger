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
        return [new DebuggerObserver(session)]
    }
}
