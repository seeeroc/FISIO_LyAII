package fisio.gui;

import javax.swing.SwingUtilities;

/**
 * Punto de entrada de la GUI FISIO — equivalente a main()/mainloop() en
 * gui.py. No contiene logica de analisis lexico/sintactico: solo crea la
 * ventana principal en el Event Dispatch Thread.
 */
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
