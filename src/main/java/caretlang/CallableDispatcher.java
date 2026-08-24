package caretlang;

import java.util.function.Supplier;

/** Owns language call-boundary behavior independently of expression evaluation. */
final class CallableDispatcher implements Value.CallInvoker {
    // Keep the language-owned guard below typical JVM stack limits so diagnostics do not depend on
    // host stack size or whether a StackOverflowError happens first.
    private static final int MAX_CALL_DEPTH = 256;
    private int depth;

    @Override public Value invoke(Value.Callable callable, Value.Argument argument, SourceSpan callSpan) {
        return withinDepth(callSpan, () -> callable.apply(argument, callSpan));
    }

    Value invokeZero(Value.Callable callable, SourceSpan callSpan) {
        return withinDepth(callSpan, () -> callable.invokeZero(callSpan));
    }

    private Value withinDepth(SourceSpan span, Supplier<Value> invocation) {
        if (depth >= MAX_CALL_DEPTH) throw exhausted(span);
        depth++;
        try {
            return invocation.get();
        } catch (StackOverflowError exhaustedStack) {
            throw exhausted(span);
        } finally {
            depth--;
        }
    }

    private static LangException exhausted(SourceSpan span) {
        return new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.CALL_DEPTH_EXCEEDED,
                "Maximum Caret call depth exceeded", span);
    }
}
