package corque.gimpalarm.common.exception;

public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
