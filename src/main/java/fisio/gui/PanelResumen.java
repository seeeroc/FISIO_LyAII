package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/** Resumen del pipeline y distribución del resultado léxico almacenado. */
public final class PanelResumen extends JPanel {
    private final JTextArea texto = new JTextArea();
    public PanelResumen() {
        super(new BorderLayout());
        texto.setEditable(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        texto.setBackground(TemaFisio.BG_PANEL);
        texto.setForeground(TemaFisio.FG_MAIN);
        JScrollPane scroll = new JScrollPane(texto);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll);
    }
    void poblar(CoordinadorAnalisis.Resultado r) {
        StringBuilder salida = new StringBuilder();
        salida.append("FASES DEL ANÁLISIS\n\n")
            .append("Léxico: ").append(r.estadoLexico().texto).append("\n")
            .append("Sintáctico: ").append(r.estadoSintactico().texto).append("\n")
            .append("Semántico: ").append(r.estadoSemantico().texto).append("\n")
            .append("Código intermedio: ").append(r.estadoCodigoIntermedio().texto).append("\n\n")
            .append("Tokens: ").append(r.lexico().tokens().size()).append("\n")
            .append("Expresiones aritméticas: ").append(r.codigoIntermedio().expresiones().size()).append("\n")
            .append("Errores encontrados: ").append(r.errores()).append("\n\nDISTRIBUCIÓN DE TOKENS\n\n");
        Map<String, Integer> conteos = new LinkedHashMap<>();
        r.lexico().tokens().forEach(t -> conteos.merge(t.categoria().name(), 1, Integer::sum));
        if (conteos.isEmpty()) salida.append("No se encontraron tokens.\n");
        conteos.forEach((tipo, cantidad) -> salida.append(tipo).append(": ").append(cantidad).append("\n"));
        salida.append("\nDIAGNÓSTICOS\n\n");
        if (r.errores() == 0) salida.append("Sin errores en las fases realizadas.\n");
        r.lexico().errores().forEach(e -> diagnostico(salida, "Léxico", e.linea(), e.columna(), e.mensaje()));
        if (r.sintactico() != null) r.sintactico().errores().forEach(e -> diagnostico(salida, "Sintáctico", e.linea(), e.columna(), e.mensaje()));
        if (r.semantico() != null) r.semantico().errores().forEach(e -> diagnostico(salida, "Semántico / " + e.codigo(), e.linea(), e.columna(), e.mensaje()));
        r.codigoIntermedio().problemas().forEach(e -> diagnostico(salida, "Código intermedio", e.linea(), e.columna(), e.mensaje()));
        texto.setText(salida.toString());
        texto.setCaretPosition(0);
    }
    private static void diagnostico(StringBuilder salida, String fase, int linea, int columna, String mensaje) {
        salida.append(fase).append(" · Línea ").append(linea).append(", columna ").append(columna)
            .append("\n").append(mensaje).append("\n\n");
    }
    public void limpiar() { texto.setText(""); }
}
