package corque.gimpalarm.common.exception;

public class ExternalApiException extends AppException {
    public ExternalApiException(String message) {
        super(ErrorCode.EXTERNAL_API_ERROR, message);
    }

    public ExternalApiException(String message, Throwable cause) {
        super(ErrorCode.EXTERNAL_API_ERROR, message, cause);
    }
}
