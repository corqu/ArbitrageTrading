package corque.gimpalarm.common.exception;

public class UnauthorizedException extends AppException {
    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.AUTHENTICATION_FAILED, message);
    }
}
