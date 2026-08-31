package caretlang;

import java.util.function.Supplier;

/** Owns language call-boundary behavior independently of expression evaluation. */
final class CallableDispatcher implements Value.CallInvoker {
    // Keep the language-owned guard below typical JVM stack limits so diagnostics do not depend on
    // host stack size or whether a StackOverflowError happens first.
    private static final int MAX_CALL_DEPTH = 256;
    private int depth;

    @Override public Value invoke(Value.Callable callable, Value.Argument argument, SourceSpan callSpan) {
        if (callable.remainingArity() == 1) requireKnownEffects(callable, callSpan);
        return withinDepth(callSpan, () -> callable.apply(argument, callSpan));
    }

    Value invokeZero(Value.Callable callable, SourceSpan callSpan) {
        requireKnownEffects(callable, callSpan);
        return withinDepth(callSpan, () -> callable.invokeZero(callSpan));
    }

    private static void requireKnownEffects(Value.Callable callable, SourceSpan span) {
        if (callable.signature().effects().upperBound() == null) {
            throw new LangException(Diagnostic.Phase.RUNTIME, Diagnostic.Codes.UNKNOWN_CALL_EFFECTS,
                    "Callable invocation has no known effect upper bound", span);
        }
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
