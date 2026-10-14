package fisio.gui;

import javax.swing.SwingUtilities;

/** Punto de entrada de la aplicación Swing. */
public final class AplicacionFisio {

    private AplicacionFisio() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setVisible(true);
        });
    }
}
