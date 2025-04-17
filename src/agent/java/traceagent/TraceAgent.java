package traceagent;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import app.debug.Trace;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

public class TraceAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        new AgentBuilder.Default()
            .type(any())
            .transform((DynamicType.Builder<?> builder, TypeDescription type, ClassLoader classLoader, JavaModule module, ProtectionDomain protectionDomain) -> builder
                .method(isAnnotatedWith(Trace.class))
                .intercept(Advice.to(TraceAdvice.class))
            )
            .installOn(inst);
    }
}
