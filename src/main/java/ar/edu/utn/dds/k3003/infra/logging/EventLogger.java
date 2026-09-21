package ar.edu.utn.dds.k3003.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único punto de emisión de eventos de negocio (logging-spec_v1.md §3.2 y §5).
 * Envuelve la fluent API de SLF4J para que event.action/event.outcome/schema_version
 * estén siempre presentes y los IDs siempre se serialicen como String.
 */
public class EventLogger {
    private static final int SCHEMA_VERSION = 1;
    private final Logger log;

    private EventLogger(Class<?> owner) {
        this.log = LoggerFactory.getLogger(owner);
    }

    public static EventLogger of(Class<?> owner) {
        return new EventLogger(owner);
    }

    public Evento evento(EventoLog e, String message) {
        return new Evento(log, e, message);
    }

    public static final class Evento {
        private final Logger log;
        private final EventoLog evento;
        private final String message;
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private Outcome outcome = Outcome.SUCCESS;
        private Level level = Level.INFO;
        private Throwable error;

        Evento(Logger log, EventoLog evento, String message) {
            this.log = log;
            this.evento = evento;
            this.message = message;
        }

        /** IDs: siempre a String, aunque el modelo use Long. */
        public Evento id(String field, Object value) {
            if (value != null) fields.put(field, String.valueOf(value));
            return this;
        }

        /** Valores no-ID (números, booleanos, enums cortos). Nunca PII. */
        public Evento dato(String field, Object value) {
            if (value != null) fields.put(field, value);
            return this;
        }

        public Evento duracion(long ms) {
            fields.put(LogFields.DURATION, ms);
            return this;
        }

        public Evento outcome(Outcome o) {
            this.outcome = o;
            return this;
        }

        public Evento warn() {
            this.level = Level.WARN;
            return this;
        }

        public Evento error(Throwable t) {
            this.level = Level.ERROR;
            this.error = t;
            this.outcome = Outcome.FAILURE;
            return this;
        }

        public void emitir() {
            LoggingEventBuilder b = log.atLevel(level)
                    .addKeyValue(LogFields.EVENT, evento.action())
                    .addKeyValue(LogFields.OUTCOME, outcome.value())
                    .addKeyValue(LogFields.SCHEMA_VER, SCHEMA_VERSION);
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                b = b.addKeyValue(entry.getKey(), entry.getValue());
            }
            if (error != null) {
                b = b.setCause(error);
            }
            b.log(message);
        }
    }
}
