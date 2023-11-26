package org.ddd.share;

/**
 * @author qiaohe
 * @date 2023/8/15
 */
public class DomainException extends RuntimeException{
    public DomainException(String message) {
        super(message);
    }
    public DomainException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
