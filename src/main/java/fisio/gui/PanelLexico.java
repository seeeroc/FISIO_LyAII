package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.*;
import fisio.analysis.ResultadoLexico;

final class PanelLexico extends JPanel {
    private final JTextArea texto = new JTextArea();
    PanelLexico() {
        super(new BorderLayout());
        texto.setEditable(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setBackground(TemaFisio.BG_PANEL);
        texto.setForeground(TemaFisio.FG_MAIN);
        texto.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(texto);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll);
    }
    void poblar(ResultadoLexico resultado) {
        StringBuilder salida = new StringBuilder("Tokens encontrados: " + resultado.tokens().size()
            + "\nErrores léxicos: " + resultado.errores().size() + "\n\n");
        resultado.errores().forEach(e -> salida.append("Línea ").append(e.linea()).append(", columna ")
            .append(e.columna()).append(" · ").append(e.lexema()).append("\n").append(e.mensaje()).append("\n\n"));
        texto.setText(salida.toString());
        texto.setCaretPosition(0);
    }
}
