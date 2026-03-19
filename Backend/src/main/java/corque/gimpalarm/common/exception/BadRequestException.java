package corque.gimpalarm.common.exception;

public class BadRequestException extends AppException {
    public BadRequestException(String message) {
        super(ErrorCode.INVALID_INPUT, message);
    }
}
