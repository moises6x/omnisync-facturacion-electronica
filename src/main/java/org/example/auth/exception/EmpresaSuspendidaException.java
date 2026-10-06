package org.example.auth.exception;

public class EmpresaSuspendidaException
        extends RuntimeException {

    public EmpresaSuspendidaException(
            String message
    ) {

        super(message);
    }
}