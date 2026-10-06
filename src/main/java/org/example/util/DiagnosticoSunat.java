package org.example.util;

/** Sólo limita texto diagnóstico; nunca se aplica a XML/ZIP/ticket/hash archivados. */
public final class DiagnosticoSunat {
    private DiagnosticoSunat() { }
    public static String acotar(String texto) {
        if (texto == null || texto.length() <= 4000) return texto;
        int fin = Character.isHighSurrogate(texto.charAt(3999)) ? 3999 : 4000;
        return texto.substring(0, fin) + " [diagnóstico resumido; evidencia íntegra archivada]";
    }
}
