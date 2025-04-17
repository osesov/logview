package app.debug;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface TraceArg {
    String value(); // used as the key name in the trace map
}
