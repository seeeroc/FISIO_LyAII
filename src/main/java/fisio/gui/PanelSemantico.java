package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import fisio.semantic.ResultadoSemantico;

/** Resultados separados de los errores lexicos y sintacticos. */
public final class PanelSemantico extends JPanel {
    private final JTextArea texto = new JTextArea();

    public PanelSemantico() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);
        texto.setEditable(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        texto.setBackground(TemaFisio.BG_PANEL);
        texto.setForeground(TemaFisio.FG_MAIN);
        texto.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        add(new JScrollPane(texto), BorderLayout.CENTER);
        limpiar();
    }

    public void poblar(ResultadoSemantico resultado) {
        if (!resultado.analizado()) {
            texto.setText("ANÁLISIS SEMÁNTICO NO REALIZADO\n\n" + resultado.motivo());
            return;
        }
        StringBuilder salida = new StringBuilder("ANÁLISIS SEMÁNTICO\n\n");
        salida.append(resultado.errores().size()).append(" error(es) semánticos.\n");
        if (resultado.exitoso()) salida.append("Sin errores en las reglas del subconjunto implementado.\n");
        for (var error : resultado.errores()) {
            salida.append("\n[Línea ").append(error.linea()).append(", Columna ").append(error.columna())
                .append("] ").append(error.codigo()).append(": ").append(error.mensaje()).append('\n');
        }
        texto.setText(salida.toString());
        texto.setCaretPosition(0);
    }

    public void limpiar() { texto.setText(""); }
}
