package corque.gimpalarm.common.exception;

public class ConflictException extends AppException {
    public ConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
