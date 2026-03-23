package corque.gimpalarm.common.exception;

public class EncryptionException extends AppException {
    public EncryptionException(String message, Throwable cause) {
        super(ErrorCode.ENCRYPTION_ERROR, message, cause);
    }
}
