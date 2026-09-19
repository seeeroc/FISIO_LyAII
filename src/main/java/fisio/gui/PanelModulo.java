package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.*;

/** Encabezado, estado y mensaje consistentes; no ejecuta análisis. */
final class PanelModulo extends JPanel {
    private final JLabel estado = new JLabel();
    private final JTextArea mensaje = new JTextArea();
    private final JComponent contenido;
    EstadoAnalisis estadoActual;

    PanelModulo(String titulo, JComponent contenido) {
        super(new BorderLayout(0, 12));
        this.contenido = contenido;
        setBackground(TemaFisio.BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel cabecera = new JPanel();
        cabecera.setOpaque(false);
        cabecera.setLayout(new BoxLayout(cabecera, BoxLayout.Y_AXIS));
        JLabel nombre = new JLabel(titulo);
        nombre.setFont(TemaFisio.fuenteUi(Font.BOLD, 20));
        nombre.setForeground(TemaFisio.FG_MAIN);
        estado.setFont(TemaFisio.fuenteUi(Font.BOLD, 12));
        mensaje.setEditable(false);
        mensaje.setOpaque(false);
        mensaje.setForeground(TemaFisio.FG_DIM);
        mensaje.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
        mensaje.setLineWrap(true);
        mensaje.setWrapStyleWord(true);
        mensaje.setRows(3);
        nombre.setAlignmentX(LEFT_ALIGNMENT);
        estado.setAlignmentX(LEFT_ALIGNMENT);
        mensaje.setAlignmentX(LEFT_ALIGNMENT);
        cabecera.add(nombre);
        cabecera.add(Box.createVerticalStrut(8));
        cabecera.add(estado);
        cabecera.add(Box.createVerticalStrut(8));
        cabecera.add(mensaje);
        add(cabecera, BorderLayout.NORTH);
        add(contenido, BorderLayout.CENTER);
        actualizar(EstadoAnalisis.NO_EJECUTADO, "Todavía no se ha realizado el análisis. Pulsa Ejecutar (F5).", false);
    }

    void actualizar(EstadoAnalisis valor, String detalle, boolean mostrarContenido) {
        estadoActual = valor;
        estado.setText(valor.texto);
        estado.setForeground(valor == EstadoAnalisis.CORRECTO ? TemaFisio.OK_GREEN
            : valor == EstadoAnalisis.CON_ERRORES || valor == EstadoAnalisis.ERROR_INTERNO ? TemaFisio.COL_ERROR : TemaFisio.ACCENT);
        mensaje.setText(detalle);
        contenido.setVisible(mostrarContenido);
        revalidate();
        repaint();
    }
}
