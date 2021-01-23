package team.catgirl.coordshare.server.http;

public abstract class HttpException extends RuntimeException {
    public final int code;

    public HttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public HttpException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static class BadRequestException extends HttpException {
        public BadRequestException(String message) {
            super(400, message);
        }
    }

    public static class UnauthorisedException extends HttpException {
        public UnauthorisedException(String message) {
            super(401, message);
        }
    }

    public static class ForbiddenException extends HttpException {
        public ForbiddenException(String message) {
            super(403, message);
        }
    }

    public final static class NotFoundException extends HttpException {
        public NotFoundException(String message) {
            super(404, message);
        }
    }

    public final static class UnmappedHttpException extends HttpException {
        public UnmappedHttpException(int httpCode, String message) {
            super(httpCode, message);
        }
    }
}