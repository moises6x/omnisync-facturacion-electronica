package org.example.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Límites técnicos antes de acumular memoria; no interpreta contenido fiscal. */
public final class LecturaLimitada {
    private LecturaLimitada() {}

    public static byte[] leer(InputStream input, int maximo) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transferir(input, maximo, output);
        return output.toByteArray();
    }

    public static int descartar(InputStream input, int maximo) throws IOException {
        return transferir(input, maximo, null);
    }

    private static int transferir(InputStream input, int maximo, ByteArrayOutputStream output) throws IOException {
        if (maximo < 0) throw new IllegalArgumentException("Límite de lectura inválido.");
        byte[] buffer = new byte[8192];
        int total = 0, leidos;
        while ((leidos = input.read(buffer, 0, (int) Math.min(buffer.length, (long) maximo - total + 1))) != -1) {
            if (leidos > maximo - total) throw new IllegalArgumentException("Respuesta SUNAT supera el límite técnico de lectura.");
            total += leidos;
            if (output != null) output.write(buffer, 0, leidos);
        }
        return total;
    }
}
