package br.com.triaige.aiservice.domain.exception;
public class OutOfScopeLegalAreaException extends RuntimeException {
    public OutOfScopeLegalAreaException(String legalArea) {
        super("Legal area is out of scope for automated analysis: " + legalArea);
    }
}
