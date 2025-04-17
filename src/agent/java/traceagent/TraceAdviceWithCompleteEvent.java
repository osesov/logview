package traceagent;

import net.bytebuddy.asm.Advice;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import app.debug.Trace;
import app.debug.TraceArg;
import app.debug.TraceLogger;

public class TraceAdviceWithCompleteEvent
{

    @Advice.OnMethodEnter
    public static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Origin Method method,
        @Advice.AllArguments Object[] args,
        @Advice.Enter long startTime
    ) {
        long duration = (System.nanoTime() - startTime) / 1000;
        String label = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        Map<String, String> traceArgs = new HashMap<>();
        Parameter[] params = method.getParameters();

        for (int i = 0; i < args.length; i++) {
            String key = null;

            // Check if parameter has @TraceArg
            for (Annotation annotation : params[i].getAnnotations()) {
                if (annotation instanceof TraceArg) {
                    key = ((TraceArg) annotation).value();
                    break;
                }
            }

            // Use parameter name if compiled with -parameters
            if (key == null || key.isEmpty()) {
                String paramName = params[i].getName(); // requires -parameters flag
                key = (paramName != null && !paramName.matches("arg\\d+")) ? paramName : "arg" + i;
            }

            String value = args[i] != null ? args[i].toString() : "null";
            traceArgs.put(key, value);
        }

        TraceLogger.complete(label, duration, traceArgs);
    }
}
