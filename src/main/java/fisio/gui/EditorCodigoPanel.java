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
import java.util.function.BiConsumer;

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
import fisio.semantic.ErrorSemantico;
import fisio.token.ErrorLexico;
import fisio.token.TokenFisio;

/** Editor con numeración de líneas y resaltado por fase de análisis. */
public final class EditorCodigoPanel extends JPanel {

    private final JTextPane texto;
    private final JTextArea numeros;
    private final List<Runnable> listenersCambio = new ArrayList<>();
    private final List<BiConsumer<Integer, Integer>> listenersCursor = new ArrayList<>();

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
        crearEstiloCategoria("STRING", TemaFisio.COL_STRING, false, false, false);
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

        texto.addCaretListener(e -> notificarCursor());
    }

    /** Registra un listener de posicion de cursor: (linea, columna), ambos base 1. */
    public void addCursorListener(BiConsumer<Integer, Integer> listener) {
        listenersCursor.add(listener);
    }

    private void notificarCursor() {
        int[] lineaColumna = calcularLineaColumna(texto.getCaretPosition());
        for (BiConsumer<Integer, Integer> listener : listenersCursor) {
            listener.accept(lineaColumna[0], lineaColumna[1]);
        }
    }

    /** Traduce una posición absoluta del documento a línea y columna, ambas desde 1. */
    private int[] calcularLineaColumna(int posicionCaret) {
        Element raiz = documentoEstilado.getDefaultRootElement();
        int indiceLinea = raiz.getElementIndex(posicionCaret);
        Element elementoLinea = raiz.getElement(indiceLinea);
        int linea = indiceLinea + 1;
        int columna = posicionCaret - elementoLinea.getStartOffset() + 1;
        return new int[]{linea, columna};
    }

    /** Registra un listener para cada inserción o eliminación de texto. */
    public void addCambioListener(Runnable listener) {
        listenersCambio.add(listener);
    }

    /** Difiere la notificación para no modificar estilos durante un evento del documento. */
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

    /** Colorea tokens y errores léxicos sin modificar el texto. */
    public void aplicarResaltado(List<TokenFisio> tokens, List<ErrorLexico> errores) {
        Set<Integer> lineasError = new LinkedHashSet<>();
        for (ErrorLexico err : errores) {
            lineasError.add(err.linea());
        }
        aplicarResaltadoBase(tokens, lineasError);

        for (ErrorLexico err : errores) {
            aplicarEstiloRango("ERROR", err.linea(), err.columna(), err.lexema().length());
        }
    }

    /** Colorea tokens y errores sintácticos sin modificar el texto. */
    public void aplicarResaltadoSintactico(List<TokenFisio> tokens, List<ErrorSintactico> erroresSintacticos) {
        Set<Integer> lineasError = new LinkedHashSet<>();
        for (ErrorSintactico err : erroresSintacticos) {
            lineasError.add(err.linea());
        }
        aplicarResaltadoBase(tokens, lineasError);

        for (ErrorSintactico err : erroresSintacticos) {
            aplicarEstiloRango("ERROR", err.linea(), err.columna(), err.token().lexema().length());
        }
    }

    public void aplicarResaltadoSemantico(List<TokenFisio> tokens, List<ErrorSemantico> errores) {
        Set<Integer> lineasError = new LinkedHashSet<>();
        for (ErrorSemantico error : errores) {
            lineasError.add(error.linea());
        }
        aplicarResaltadoBase(tokens, lineasError);

        for (ErrorSemantico error : errores) {
            int longitud = tokens.stream()
                .filter(t -> t.linea() == error.linea() && t.columna() == error.columna())
                .mapToInt(t -> t.lexema().length()).findFirst().orElse(1);
            aplicarEstiloRango("ERROR", error.linea(), error.columna(), longitud);
        }
    }

    private void aplicarResaltadoBase(List<TokenFisio> tokens, Set<Integer> lineasError) {
        int longitud = documentoEstilado.getLength();
        documentoEstilado.setCharacterAttributes(0, longitud, estiloBase, true);

        for (int linea : lineasError) {
            int inicio = offsetDe(linea, 1);
            int fin = finDeLinea(linea);
            if (fin > inicio) {
                documentoEstilado.setCharacterAttributes(inicio, fin - inicio, estiloLineaError, false);
            }
        }

        for (TokenFisio token : tokens) {
            aplicarEstiloRango(token.categoria().name(), token.linea(), token.columna(), token.lexema().length());
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

    /** Offset de fin de línea, antes del salto. */
    private int finDeLinea(int linea) {
        Element raiz = documentoEstilado.getDefaultRootElement();
        int indiceLinea = Math.max(0, Math.min(linea - 1, raiz.getElementCount() - 1));
        Element elementoLinea = raiz.getElement(indiceLinea);
        int fin = elementoLinea.getEndOffset() - 1;
        return Math.max(elementoLinea.getStartOffset(), Math.min(fin, documentoEstilado.getLength()));
    }
}
