package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import fisio.analysis.ErrorSintactico;
import fisio.token.ErrorLexico;
import fisio.token.TokenFisio;

/**
 * Editor de codigo con numeracion de lineas, equivalente visual a
 * EditorCodigo (gui.py).
 *
 * Desde el Paso 6C incluye resaltado lexico real (equivalente a
 * EditorCodigo.resaltar() en Python), aplicado como atributos de caracter
 * sobre el StyledDocument del JTextPane -- nunca se modifica el texto.
 *
 * Los numeros de linea se implementan como row header del mismo
 * JScrollPane que el texto principal — a diferencia de gui.py (que
 * sincroniza manualmente dos widgets Text porque Tkinter no tiene
 * row header nativo), esto es la solucion Swing estandar y sincroniza el
 * scroll automaticamente sin codigo adicional.
 */
public final class EditorCodigoPanel extends JPanel {

    private final JTextPane texto;
    private final JTextArea numeros;
    private final List<Runnable> listenersCambio = new ArrayList<>();

    private final StyledDocument documentoEstilado;
    private final Style estiloBase;
    private final Style estiloLineaError;
    private final Map<String, Style> estilosPorCategoria = new LinkedHashMap<>();

    public EditorCodigoPanel() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_EDITOR);

        texto = new JTextPane();
        texto.setBackground(TemaFisio.BG_EDITOR);
        texto.setForeground(TemaFisio.FG_MAIN);
        texto.setCaretColor(TemaFisio.ACCENT);
        texto.setSelectionColor(TemaFisio.BORDER);
        texto.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        texto.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        numeros = new JTextArea("1");
        numeros.setEditable(false);
        numeros.setFocusable(false);
        numeros.setBackground(TemaFisio.BG_EDITOR);
        numeros.setForeground(TemaFisio.FG_LINENO);
        numeros.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        numeros.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));

        JScrollPane scroll = new JScrollPane(texto);
        scroll.setRowHeaderView(numeros);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        add(scroll, BorderLayout.CENTER);

        documentoEstilado = texto.getStyledDocument();
        estiloBase = documentoEstilado.addStyle("BASE", null);
        StyleConstants.setForeground(estiloBase, TemaFisio.FG_MAIN);
        StyleConstants.setBackground(estiloBase, TemaFisio.BG_EDITOR);
        StyleConstants.setBold(estiloBase, false);
        StyleConstants.setItalic(estiloBase, false);
        StyleConstants.setUnderline(estiloBase, false);

        estiloLineaError = documentoEstilado.addStyle("linea_error", null);
        StyleConstants.setBackground(estiloLineaError, Color.decode("#2d1b1b"));

        crearEstiloCategoria("PR", TemaFisio.COL_PR, true, false, false);
        crearEstiloCategoria("OPM", TemaFisio.COL_OPM, false, false, false);
        crearEstiloCategoria("OPR", TemaFisio.COL_OPR, false, false, false);
        crearEstiloCategoria("SIG", TemaFisio.COL_SIG, false, false, false);
        crearEstiloCategoria("ID", TemaFisio.COL_ID, false, false, false);
        crearEstiloCategoria("NUM", TemaFisio.COL_NUM, false, false, false);
        crearEstiloCategoria("ERROR", TemaFisio.COL_ERROR, false, true, true);

        texto.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                actualizarNumeros();
                notificarCambio();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                actualizarNumeros();
                notificarCambio();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
    }

    /**
     * Se invoca en cada insercion/borrado del documento -- tanto por
     * tipeo del usuario como por setCodigo()/limpiar() programaticos (el
     * llamador es responsable de distinguirlos si le importa, p.ej.
     * restableciendo su propio flag justo despues de llamar setCodigo()).
     * Preparado para 6C: la GUI necesitara saber si el codigo cambio desde
     * el ultimo analisis lexico (codigo analizado vs. codigo actual).
     */
    public void addCambioListener(Runnable listener) {
        listenersCambio.add(listener);
    }

    /**
     * Se difiere con invokeLater porque este metodo se invoca DESDE el
     * propio insertUpdate/removeUpdate del DocumentListener del editor --
     * si un listener (p.ej. la invalidacion de analisis en
     * VentanaPrincipal) intentara mutar los atributos del mismo Document
     * de forma sincrona aqui (aplicarResaltado -> setCharacterAttributes),
     * Swing lanza "IllegalStateException: Attempt to mutate in
     * notification" porque el Document todavia esta notificando este
     * mismo cambio. Diferir al siguiente ciclo del EDT evita el problema
     * sin cambiar el comportamiento observable (ocurre en el mismo golpe
     * de tecla, sin demora perceptible).
     */
    private void notificarCambio() {
        for (Runnable listener : listenersCambio) {
            SwingUtilities.invokeLater(listener);
        }
    }

    private void actualizarNumeros() {
        int lineas = texto.getDocument().getDefaultRootElement().getElementCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineas; i++) {
            if (i > 1) {
                sb.append('\n');
            }
            sb.append(i);
        }
        numeros.setText(sb.toString());
    }

    public String getCodigo() {
        return texto.getText();
    }

    public void setCodigo(String codigo) {
        texto.setText(codigo);
        texto.setCaretPosition(0);
        actualizarNumeros();
    }

    public void limpiar() {
        setCodigo("");
    }

    private void crearEstiloCategoria(String nombre, Color color, boolean negrita, boolean cursiva, boolean subrayado) {
        Style estilo = documentoEstilado.addStyle(nombre, null);
        StyleConstants.setForeground(estilo, color);
        StyleConstants.setBold(estilo, negrita);
        StyleConstants.setItalic(estilo, cursiva);
        StyleConstants.setUnderline(estilo, subrayado);
        estilosPorCategoria.put(nombre, estilo);
    }

    /**
     * Equivalente a EditorCodigo.resaltar(tokens, errores) en gui.py:
     * 1) restablece TODO el documento al estilo base (evita que un analisis
     *    anterior deje colores residuales); 2) marca de fondo las lineas
     * con error; 3) colorea cada token por categoria (incluye los tokens
     * categoria=ERROR, que ya vienen dentro de "tokens" -- ver
     * ResultadoLexico); 4) subraya el lexema exacto de cada error. Llamar
     * con dos listas vacias equivale a "limpiar resaltado". Nunca modifica
     * el texto del documento, solo sus atributos de caracter.
     */
    public void aplicarResaltado(List<TokenFisio> tokens, List<ErrorLexico> errores) {
        int longitud = documentoEstilado.getLength();
        documentoEstilado.setCharacterAttributes(0, longitud, estiloBase, true);

        Set<Integer> lineasError = new LinkedHashSet<>();
        for (ErrorLexico err : errores) {
            lineasError.add(err.linea());
        }
        for (int linea : lineasError) {
            int inicio = offsetDe(linea, 1);
            int fin = finDeLinea(linea);
            if (fin > inicio) {
                documentoEstilado.setCharacterAttributes(inicio, fin - inicio, estiloLineaError, false);
            }
        }

        for (TokenFisio tok : tokens) {
            aplicarEstiloRango(tok.categoria().name(), tok.linea(), tok.columna(), tok.lexema().length());
        }

        for (ErrorLexico err : errores) {
            aplicarEstiloRango("ERROR", err.linea(), err.columna(), err.lexema().length());
        }
    }

    /**
     * Equivalente a aplicarResaltado(tokens, errores) pero para el
     * resultado sintactico (F6): gui.py reutiliza el mismo
     * EditorCodigo.resaltar(tokens, errores) para ambos casos porque en
     * Python ErrorLexico/ErrorSintactico se acceden por duck typing
     * (.linea/.columna/.lexema). En Java no existe una interfaz comun
     * entre esos dos records congelados (ni se debe crear una para esto),
     * asi que se expone un segundo metodo con la misma logica en vez de
     * forzar una abstraccion artificial entre fisio.token y
     * fisio.analysis. "tokens" sigue siendo la lista lexica de F5 (mismo
     * criterio que el self._tokens que Python reutiliza en
     * _analizar_sintactico); solo la fuente de los errores cambia.
     */
    public void aplicarResaltadoSintactico(List<TokenFisio> tokens, List<ErrorSintactico> erroresSintacticos) {
        int longitud = documentoEstilado.getLength();
        documentoEstilado.setCharacterAttributes(0, longitud, estiloBase, true);

        Set<Integer> lineasError = new LinkedHashSet<>();
        for (ErrorSintactico err : erroresSintacticos) {
            lineasError.add(err.linea());
        }
        for (int linea : lineasError) {
            int inicio = offsetDe(linea, 1);
            int fin = finDeLinea(linea);
            if (fin > inicio) {
                documentoEstilado.setCharacterAttributes(inicio, fin - inicio, estiloLineaError, false);
            }
        }

        for (TokenFisio tok : tokens) {
            aplicarEstiloRango(tok.categoria().name(), tok.linea(), tok.columna(), tok.lexema().length());
        }

        for (ErrorSintactico err : erroresSintacticos) {
            aplicarEstiloRango("ERROR", err.linea(), err.columna(), err.token().lexema().length());
        }
    }

    private void aplicarEstiloRango(String nombreEstilo, int linea, int columna, int longitudLexema) {
        if (longitudLexema <= 0) {
            return;
        }
        Style estilo = estilosPorCategoria.get(nombreEstilo);
        if (estilo == null) {
            return;
        }
        int inicio = offsetDe(linea, columna);
        int fin = Math.min(inicio + longitudLexema, documentoEstilado.getLength());
        if (fin > inicio) {
            documentoEstilado.setCharacterAttributes(inicio, fin - inicio, estilo, false);
        }
    }

    /** Convierte (linea, columna) 1-base de FISIO a un offset absoluto del Document. */
    private int offsetDe(int linea, int columna) {
        Element raiz = documentoEstilado.getDefaultRootElement();
        int indiceLinea = Math.max(0, Math.min(linea - 1, raiz.getElementCount() - 1));
        Element elementoLinea = raiz.getElement(indiceLinea);
        int offset = elementoLinea.getStartOffset() + (columna - 1);
        return Math.max(0, Math.min(offset, documentoEstilado.getLength()));
    }

    /** Offset de fin de linea (antes del salto de linea), equivalente a Tk "N.end". */
    private int finDeLinea(int linea) {
        Element raiz = documentoEstilado.getDefaultRootElement();
        int indiceLinea = Math.max(0, Math.min(linea - 1, raiz.getElementCount() - 1));
        Element elementoLinea = raiz.getElement(indiceLinea);
        int fin = elementoLinea.getEndOffset() - 1;
        return Math.max(elementoLinea.getStartOffset(), Math.min(fin, documentoEstilado.getLength()));
    }
}
