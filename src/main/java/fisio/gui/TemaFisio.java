package fisio.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.UIManager;

/**
 * Colores y fuentes de la GUI FISIO, centralizados en un solo lugar.
 *
 * Los colores son una traduccion 1:1 de COLORES_GUI (token_types.py) — no se
 * agrega ni se quita ningun valor. Las fuentes reproducen la eleccion de
 * gui.py (Consolas/Segoe UI en Windows), con una alternativa de sistema
 * (fuentes logicas de Java, garantizadas por el JDK) si esas familias no
 * estan instaladas — gui.py resuelve la misma idea usando Courier New en
 * sistemas no-Windows; aqui se delega en las fuentes logicas de Java
 * (MONOSPACED/SANS_SERIF) en vez de fijar una familia concreta.
 */
public final class TemaFisio {

    public static final Color BG_MAIN = Color.decode("#1e1e2e");
    public static final Color BG_EDITOR = Color.decode("#181825");
    public static final Color BG_PANEL = Color.decode("#11111b");
    public static final Color BG_TOOLBAR = Color.decode("#313244");
    public static final Color BG_STATUS = Color.decode("#181825");
    public static final Color BG_ROW_ALT = Color.decode("#1e1e2e");
    public static final Color BORDER = Color.decode("#45475a");
    public static final Color FG_MAIN = Color.decode("#cdd6f4");
    public static final Color FG_DIM = Color.decode("#a6adc8");
    public static final Color FG_LINENO = Color.decode("#6c7086");
    public static final Color ACCENT = Color.decode("#89b4fa");
    public static final Color OK_GREEN = Color.decode("#a6e3a1");
    public static final Color COL_PR = Color.decode("#cba6f7");
    public static final Color COL_OPM = Color.decode("#f9e2af");
    public static final Color COL_OPR = Color.decode("#fab387");
    public static final Color COL_SIG = Color.decode("#89dceb");
    public static final Color COL_ID = Color.decode("#cdd6f4");
    public static final Color COL_NUM = Color.decode("#a6e3a1");
    public static final Color COL_STRING = Color.decode("#94e2d5");
    public static final Color COL_ERROR = Color.decode("#f38ba8");

    static {
        // Equivalente a disabledforeground=C["fg_dim"] en gui.py
        // (_set_sintactico_disponible): sin esto, Swing pinta el texto de
        // cualquier boton deshabilitado con el color propio del L&F
        // (UIManager "Button.disabledText"), ignorando el
        // setForeground()/setBackground() del boton -- en Metal, ese color
        // por defecto no fue verificado contra BG_TOOLBAR. Se fija una
        // sola vez, de forma global, en vez de por boton.
        UIManager.put("Button.disabledText", FG_DIM);
    }

    private static final String FAMILIA_MONO =
        familiaDisponible("Consolas", Font.MONOSPACED);
    private static final String FAMILIA_UI =
        familiaDisponible("Segoe UI", Font.SANS_SERIF);

    private TemaFisio() {
    }

    private static String familiaDisponible(String preferida, String reemplazoLogico) {
        Set<String> disponibles = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        return disponibles.contains(preferida) ? preferida : reemplazoLogico;
    }

    /** Fuente monoespaciada del editor/tabla de tokens/panel sintactico. */
    public static Font fuenteMono(int estilo, int tamano) {
        return new Font(FAMILIA_MONO, estilo, tamano);
    }

    /** Fuente de interfaz (toolbar, tabs, status bar, etiquetas). */
    public static Font fuenteUi(int estilo, int tamano) {
        return new Font(FAMILIA_UI, estilo, tamano);
    }
}
