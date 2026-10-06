package org.example.util;

/** Límite técnico compartido, no formato normativo ni transformación del ticket. */
public final class TicketSunat {
    public static final int MAX_LENGTH = 150;
    private TicketSunat() {}

    public static void validarLongitud(String ticket) {
        if (ticket != null && ticket.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Ticket SUNAT excede el límite técnico de 150 caracteres.");
        }
    }
}
