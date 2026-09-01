package caretlang.embedding;

@SuppressWarnings("serial")
public final class CaretEmbeddingException extends RuntimeException {
    public enum Code {
        ALREADY_LOADED, HANDLE_CONSUMED, BUSY, CLOSED, FOREIGN_HANDLE, STALE_HANDLE,
        INVALID_ARGUMENT, INVALID_ARITY
    }

    private final Code code;

    public CaretEmbeddingException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    public Code code() { return code; }
}
