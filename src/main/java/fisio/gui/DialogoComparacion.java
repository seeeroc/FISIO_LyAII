package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

/** Comparación ampliada y navegable entre el código original y el optimizado. */
public final class DialogoComparacion extends JDialog {
    private static final String TODAS = "Todas las técnicas";
    private final JTextArea original = areaCodigo();
    private final JTextArea optimizado = areaCodigo();
    private final JScrollPane scrollOriginal = scroll(original);
    private final JScrollPane scrollOptimizado = scroll(optimizado);
    private final JLabel posicion = etiqueta("Sin cambios", Font.BOLD, 12, TemaFisio.ACCENT);
    private final JTextArea explicacion = areaExplicacion();
    private final JComboBox<String> filtro = new JComboBox<>();
    private final JButton anterior = boton("◀ Anterior");
    private final JButton siguiente = boton("Siguiente ▶");
    private List<PanelOptimizacion.CambioVista> todos = List.of();
    private List<PanelOptimizacion.CambioVista> visibles = List.of();
    private int indice = -1;
    private boolean sincronizando;

    public DialogoComparacion(Window propietario) {
        super(propietario, "FISIO — Comparación de optimización", ModalityType.MODELESS);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(820, 540));
        setLocationRelativeTo(propietario);
        getContentPane().setBackground(TemaFisio.BG_MAIN);
        setLayout(new BorderLayout(0, 1));
        add(construirBarra(), BorderLayout.NORTH);
        add(construirComparacion(), BorderLayout.CENTER);
        add(construirDetalle(), BorderLayout.SOUTH);
        sincronizarScroll();
        getRootPane().registerKeyboardAction(e -> setVisible(false),
            KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel construirBarra() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(TemaFisio.BG_TOOLBAR);
        barra.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        JPanel navegacion = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        navegacion.setOpaque(false);
        anterior.addActionListener(e -> mover(-1));
        siguiente.addActionListener(e -> mover(1));
        navegacion.add(anterior);
        navegacion.add(siguiente);
        navegacion.add(posicion);
        barra.add(navegacion, BorderLayout.WEST);

        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        filtros.setOpaque(false);
        JLabel nombre = etiqueta("Técnica:", Font.PLAIN, 12, TemaFisio.FG_DIM);
        filtros.add(nombre);
        filtro.setPreferredSize(new Dimension(240, 30));
        filtro.setBackground(TemaFisio.BG_MAIN);
        filtro.setForeground(TemaFisio.FG_MAIN);
        filtro.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
        filtro.addActionListener(e -> aplicarFiltro());
        filtros.add(filtro);
        JButton cerrar = boton("Cerrar");
        cerrar.addActionListener(e -> setVisible(false));
        filtros.add(cerrar);
        barra.add(filtros, BorderLayout.EAST);
        return barra;
    }

    private JSplitPane construirComparacion() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            bloque("CÓDIGO ORIGINAL", scrollOriginal, TemaFisio.COL_ERROR),
            bloque("CÓDIGO OPTIMIZADO", scrollOptimizado, TemaFisio.OK_GREEN));
        split.setResizeWeight(.5);
        split.setDividerLocation(.5);
        split.setDividerSize(6);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private JPanel construirDetalle() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(TemaFisio.BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        explicacion.setRows(3);
        panel.add(explicacion);
        return panel;
    }

    public void mostrar(String codigoOriginal, String codigoOptimizado,
            List<PanelOptimizacion.CambioVista> cambios) {
        original.setText(codigoOriginal == null ? "" : codigoOriginal);
        optimizado.setText(codigoOptimizado == null ? "" : codigoOptimizado);
        instalarNumeros(scrollOriginal, original.getLineCount());
        instalarNumeros(scrollOptimizado, optimizado.getLineCount());
        original.setCaretPosition(0);
        optimizado.setCaretPosition(0);
        todos = cambios == null ? List.of() : List.copyOf(cambios);
        reconstruirFiltro();
        aplicarFiltro();
        setLocationRelativeTo(getOwner());
        setVisible(true);
        toFront();
    }

    public void seleccionarCambio(int numero) {
        for (int i = 0; i < visibles.size(); i++) {
            if (visibles.get(i).numero() == numero) {
                indice = i;
                presentarActual();
                return;
            }
        }
    }

    private void reconstruirFiltro() {
        Object seleccion = filtro.getSelectedItem();
        filtro.removeAllItems();
        filtro.addItem(TODAS);
        Set<String> tecnicas = new LinkedHashSet<>();
        todos.forEach(c -> tecnicas.add(c.tecnica()));
        tecnicas.forEach(filtro::addItem);
        if (seleccion != null) filtro.setSelectedItem(seleccion);
        if (filtro.getSelectedIndex() < 0) filtro.setSelectedIndex(0);
    }

    private void aplicarFiltro() {
        String tecnica = (String) filtro.getSelectedItem();
        if (tecnica == null || TODAS.equals(tecnica)) visibles = todos;
        else visibles = todos.stream().filter(c -> tecnica.equals(c.tecnica())).toList();
        indice = visibles.isEmpty() ? -1 : 0;
        presentarActual();
    }

    private void mover(int desplazamiento) {
        if (visibles.isEmpty()) return;
        indice = Math.floorMod(indice + desplazamiento, visibles.size());
        presentarActual();
    }

    private void presentarActual() {
        limpiarResaltado();
        boolean disponible = indice >= 0 && indice < visibles.size();
        anterior.setEnabled(disponible && visibles.size() > 1);
        siguiente.setEnabled(disponible && visibles.size() > 1);
        if (!disponible) {
            posicion.setText("Sin cambios");
            explicacion.setText("No hay cambios para la técnica seleccionada.");
            return;
        }
        PanelOptimizacion.CambioVista cambio = visibles.get(indice);
        posicion.setText("Cambio " + (indice + 1) + " de " + visibles.size() + " · " + cambio.tecnica());
        explicacion.setText(cambio.explicacion() + "\nOriginal: " + cambio.ubicacionOriginal()
            + " · Optimizado: " + cambio.ubicacionOptimizada());
        resaltar(original, cambio.lineaOriginalInicio(), cambio.lineaOriginalFin(), Color.decode("#4a222d"));
        resaltar(optimizado, cambio.lineaOptimizadaInicio(), cambio.lineaOptimizadaFin(), Color.decode("#183c2a"));
        explicacion.setCaretPosition(0);
    }

    private void limpiarResaltado() {
        original.getHighlighter().removeAllHighlights();
        optimizado.getHighlighter().removeAllHighlights();
    }

    private static void resaltar(JTextArea area, int inicio, int fin, Color color) {
        if (inicio < 1) return;
        try {
            int lineaInicio = Math.min(inicio - 1, area.getLineCount() - 1);
            int lineaFin = Math.min(Math.max(fin, inicio) - 1, area.getLineCount() - 1);
            int desde = area.getLineStartOffset(lineaInicio);
            int hasta = area.getLineEndOffset(lineaFin);
            area.getHighlighter().addHighlight(desde, hasta,
                new DefaultHighlighter.DefaultHighlightPainter(color));
            area.setCaretPosition(desde);
        } catch (BadLocationException ignored) {
            // Los rangos provienen del resultado, pero una edición manual puede volverlos obsoletos.
        }
    }

    private void sincronizarScroll() {
        ChangeListener izquierda = e -> copiarScroll(scrollOriginal, scrollOptimizado);
        ChangeListener derecha = e -> copiarScroll(scrollOptimizado, scrollOriginal);
        scrollOriginal.getVerticalScrollBar().getModel().addChangeListener(izquierda);
        scrollOptimizado.getVerticalScrollBar().getModel().addChangeListener(derecha);
    }

    private void copiarScroll(JScrollPane origen, JScrollPane destino) {
        if (sincronizando) return;
        sincronizando = true;
        var barraOrigen = origen.getVerticalScrollBar();
        var barraDestino = destino.getVerticalScrollBar();
        int maxOrigen = barraOrigen.getMaximum() - barraOrigen.getVisibleAmount();
        int maxDestino = barraDestino.getMaximum() - barraDestino.getVisibleAmount();
        int valor = maxOrigen <= 0 ? 0 : (int) Math.round(barraOrigen.getValue() * (double) maxDestino / maxOrigen);
        barraDestino.setValue(valor);
        sincronizando = false;
    }

    private static JPanel bloque(String titulo, JScrollPane scroll, Color color) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(TemaFisio.BG_PANEL);
        JLabel label = etiqueta(titulo, Font.BOLD, 12, color);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll);
        return panel;
    }

    private static JButton boton(String texto) {
        JButton boton = new JButton(texto);
        boton.setFont(TemaFisio.fuenteUi(Font.BOLD, 11));
        boton.setBackground(TemaFisio.BG_MAIN);
        boton.setForeground(TemaFisio.FG_MAIN);
        boton.setFocusPainted(false);
        boton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return boton;
    }

    private static JLabel etiqueta(String texto, int estilo, int tamano, Color color) {
        JLabel label = new JLabel(texto);
        label.setFont(TemaFisio.fuenteUi(estilo, tamano));
        label.setForeground(color);
        return label;
    }

    private static JTextArea areaCodigo() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setBackground(TemaFisio.BG_EDITOR);
        area.setForeground(TemaFisio.FG_MAIN);
        area.setCaretColor(TemaFisio.ACCENT);
        area.setSelectionColor(TemaFisio.BORDER);
        area.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private static JTextArea areaExplicacion() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(TemaFisio.BG_PANEL);
        area.setForeground(TemaFisio.FG_MAIN);
        area.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    private static JScrollPane scroll(JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(TemaFisio.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static void instalarNumeros(JScrollPane scroll, int cantidad) {
        StringBuilder texto = new StringBuilder();
        for (int numero = 1; numero <= Math.max(1, cantidad); numero++) {
            if (numero > 1) texto.append('\n');
            texto.append(numero);
        }
        JTextArea numeros = new JTextArea(texto.toString());
        numeros.setEditable(false);
        numeros.setFocusable(false);
        numeros.setBackground(TemaFisio.BG_EDITOR);
        numeros.setForeground(TemaFisio.FG_LINENO);
        numeros.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        numeros.setBorder(BorderFactory.createEmptyBorder(8, 5, 8, 5));
        scroll.setRowHeaderView(numeros);
    }
}
