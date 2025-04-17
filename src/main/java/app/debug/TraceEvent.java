package app.debug;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceEvent {
    public String name;
    public String ph; // phase
    public long ts;   // timestamp in microseconds
    public int pid;   // process ID
    public int tid;   // thread ID
    public String cat;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> args;

    @JsonProperty("dur")
    public Long duration;

    public TraceEvent(String name, String ph, long ts, int pid, int tid, Map<String, String> args) {
        this.name = name;
        this.ph = ph;
        this.ts = ts;
        this.pid = pid;
        this.tid = tid;
        this.args = args;
    }

    public TraceEvent(String name, String ph, long ts, int pid, int tid, Map<String, String> args, long duration) {
        this(name, ph, ts, pid, tid, args);
        this.duration = duration;
    }
}
