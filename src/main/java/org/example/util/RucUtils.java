package org.example.util;

public final class RucUtils {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucUtils() {
    }

    public static boolean esValido(String ruc) {

        if (ruc == null || !ruc.matches("^(10|15|17|20)[0-9]{9}$")) {
            return false;
        }

        int suma = 0;

        for (int i = 0; i < 10; i++) {
            suma += Character.digit(ruc.charAt(i), 10) * PESOS[i];
        }

        int digito = 11 - (suma % 11);

        if (digito == 10) digito = 0;
        if (digito == 11) digito = 1;

        int esperado = Character.digit(ruc.charAt(10), 10);

        return digito == esperado;
    }
}
