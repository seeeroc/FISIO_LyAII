package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import fisio.optimization.CambioOptimizacion;
import fisio.optimization.MetricasOptimizacion;
import fisio.optimization.ResultadoOptimizacion;

/** Resumen navegable de una optimización; la comparación completa vive en un diálogo separado. */
public final class PanelOptimizacion extends JPanel {
    public record ResumenVista(
            int lineasOriginales,
            int lineasOptimizadas,
            long bytesOriginales,
            long bytesOptimizados,
            int cambios,
            double tiempoMilisegundos,
            int cuadruplosOriginales,
            int cuadruplosOptimizados,
            Map<String, Integer> tecnicas) {
        public ResumenVista {
            tecnicas = tecnicas == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(tecnicas));
        }
    }

    public record CambioVista(
            int numero,
            String tecnica,
            int lineaOriginalInicio,
            int lineaOriginalFin,
            int lineaOptimizadaInicio,
            int lineaOptimizadaFin,
            String antes,
            String despues,
            String explicacion) {
        public CambioVista {
            tecnica = textoSeguro(tecnica);
            antes = textoSeguro(antes);
            despues = textoSeguro(despues);
            explicacion = textoSeguro(explicacion);
        }

        public String ubicacionOriginal() {
            return rango(lineaOriginalInicio, lineaOriginalFin);
        }

        public String ubicacionOptimizada() {
            return rango(lineaOptimizadaInicio, lineaOptimizadaFin);
        }

        private static String rango(int inicio, int fin) {
            if (inicio < 1) return "Sin línea equivalente";
            return inicio == fin || fin < inicio ? "Línea " + inicio : "Líneas " + inicio + "–" + fin;
        }
    }

    private final JTabbedPane pestanas = new JTabbedPane();
    private final JPanel tarjetas = new JPanel(new GridLayout(3, 2, 10, 10));
    private final JTextArea listaTecnicas = areaLectura(false);
    private final DefaultListModel<CambioVista> modeloCambios = new DefaultListModel<>();
    private final JList<CambioVista> cambios = new JList<>(modeloCambios);
    private final JLabel tituloDetalle = etiqueta("Selecciona un cambio", Font.BOLD, 15, TemaFisio.ACCENT);
    private final JLabel ubicacionDetalle = etiqueta("", Font.PLAIN, 12, TemaFisio.FG_DIM);
    private final JTextArea explicacion = areaLectura(true);
    private final JTextArea antes = areaCodigo();
    private final JTextArea despues = areaCodigo();
    private final JTextArea reporte = areaLectura(false);
    private final List<Consumer<CambioVista>> listenersSeleccion = new ArrayList<>();
    private List<CambioVista> cambiosActuales = List.of();
    private final JButton comparar = botonAccion("Ver comparación");
    private final JButton restaurar = botonAccion("Restaurar original");
    private final JButton guardar = botonAccion("Guardar optimizado");
    private final JButton exportar = botonAccion("Exportar TXT");
    private Runnable accionComparar;
    private Runnable accionRestaurar;
    private Runnable accionGuardar;
    private Runnable accionExportar;

    public PanelOptimizacion() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);
        pestanas.setFont(TemaFisio.fuenteUi(Font.BOLD, 12));
        pestanas.setBackground(TemaFisio.BG_TOOLBAR);
        pestanas.setForeground(TemaFisio.FG_MAIN);
        pestanas.addTab("Resumen", construirResumen());
        pestanas.addTab("Cambios", construirCambios());
        pestanas.addTab("Reporte", scroll(reporte));
        for (int i = 0; i < pestanas.getTabCount(); i++) {
            pestanas.setBackgroundAt(i, TemaFisio.BG_TOOLBAR);
            pestanas.setForegroundAt(i, TemaFisio.FG_MAIN);
            JLabel etiqueta = etiqueta(pestanas.getTitleAt(i), Font.BOLD, 11, TemaFisio.FG_MAIN);
            etiqueta.setOpaque(true);
            etiqueta.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            pestanas.setTabComponentAt(i, etiqueta);
        }
        pestanas.addChangeListener(e -> actualizarPestanas());
        actualizarPestanas();
        add(construirAcciones(), BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
        comparar.addActionListener(e -> ejecutar(accionComparar));
        restaurar.addActionListener(e -> ejecutar(accionRestaurar));
        guardar.addActionListener(e -> ejecutar(accionGuardar));
        exportar.addActionListener(e -> ejecutar(accionExportar));
        limpiar();
    }

    private JPanel construirAcciones() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 7, 6));
        panel.setBackground(TemaFisio.BG_TOOLBAR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        panel.add(comparar);
        panel.add(restaurar);
        panel.add(guardar);
        panel.add(exportar);
        return panel;
    }

    private void actualizarPestanas() {
        for (int i = 0; i < pestanas.getTabCount(); i++) {
            JLabel etiqueta = (JLabel) pestanas.getTabComponentAt(i);
            boolean seleccionada = i == pestanas.getSelectedIndex();
            etiqueta.setBackground(seleccionada ? TemaFisio.ACCENT : TemaFisio.BG_TOOLBAR);
            etiqueta.setForeground(seleccionada ? TemaFisio.BG_MAIN : TemaFisio.FG_MAIN);
        }
    }

    private JPanel construirResumen() {
        JPanel panel = base(new BorderLayout(0, 14));
        tarjetas.setOpaque(false);
        panel.add(tarjetas, BorderLayout.NORTH);
        JPanel tecnicas = base(new BorderLayout(0, 8));
        tecnicas.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(TemaFisio.BORDER), "Técnicas aplicadas", 0, 0,
            TemaFisio.fuenteUi(Font.BOLD, 12), TemaFisio.ACCENT));
        tecnicas.add(scroll(listaTecnicas));
        panel.add(tecnicas, BorderLayout.CENTER);
        return panel;
    }

    private JPanel construirCambios() {
        cambios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cambios.setBackground(TemaFisio.BG_EDITOR);
        cambios.setForeground(TemaFisio.FG_MAIN);
        cambios.setSelectionBackground(Color.decode("#29436a"));
        cambios.setSelectionForeground(TemaFisio.FG_MAIN);
        cambios.setFixedCellHeight(58);
        cambios.setCellRenderer(new RenderCambio());
        cambios.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) mostrarDetalle(cambios.getSelectedValue());
        });

        JPanel detalle = base(new BorderLayout(0, 10));
        detalle.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JPanel encabezado = base(new BorderLayout());
        encabezado.setBorder(BorderFactory.createEmptyBorder());
        encabezado.add(tituloDetalle, BorderLayout.NORTH);
        encabezado.add(ubicacionDetalle, BorderLayout.SOUTH);
        detalle.add(encabezado, BorderLayout.NORTH);

        JPanel contenido = base(new BorderLayout(0, 10));
        contenido.setBorder(BorderFactory.createEmptyBorder());
        explicacion.setRows(2);
        contenido.add(explicacion, BorderLayout.NORTH);
        JSplitPane codigo = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            bloqueCodigo("Antes", antes), bloqueCodigo("Después", despues));
        codigo.setResizeWeight(.5);
        codigo.setDividerLocation(.5);
        codigo.setDividerSize(5);
        codigo.setContinuousLayout(true);
        codigo.setBorder(BorderFactory.createEmptyBorder());
        contenido.add(codigo, BorderLayout.CENTER);
        detalle.add(contenido, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll(cambios), detalle);
        split.setResizeWeight(.30);
        split.setDividerLocation(150);
        split.setDividerSize(5);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        JPanel panel = base(new BorderLayout());
        panel.add(split);
        return panel;
    }

    private static JPanel bloqueCodigo(String titulo, JTextArea area) {
        JPanel panel = base(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createEmptyBorder());
        JLabel nombre = etiqueta(titulo, Font.BOLD, 12,
            "Antes".equals(titulo) ? TemaFisio.COL_ERROR : TemaFisio.OK_GREEN);
        nombre.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        panel.add(nombre, BorderLayout.NORTH);
        panel.add(scroll(area));
        return panel;
    }

    public void poblar(ResumenVista resumen, List<CambioVista> nuevosCambios, String textoReporte) {
        tarjetas.removeAll();
        tarjetas.add(tarjeta("CAMBIOS", Integer.toString(resumen.cambios()), TemaFisio.ACCENT));
        tarjetas.add(tarjeta("LÍNEAS", resumen.lineasOriginales() + " → " + resumen.lineasOptimizadas(), TemaFisio.COL_PR));
        tarjetas.add(tarjeta("TAMAÑO", formatoBytes(resumen.bytesOriginales()) + " → "
            + formatoBytes(resumen.bytesOptimizados()), TemaFisio.COL_STRING));
        tarjetas.add(tarjeta("REDUCCIÓN", porcentaje(resumen.bytesOriginales(), resumen.bytesOptimizados()), TemaFisio.OK_GREEN));
        tarjetas.add(tarjeta("TIEMPO OPT.", String.format("%.3f ms", resumen.tiempoMilisegundos()), TemaFisio.COL_OPM));
        tarjetas.add(tarjeta("CUÁDRUPLOS", resumen.cuadruplosOriginales() + " → "
            + resumen.cuadruplosOptimizados(), TemaFisio.COL_SIG));

        StringBuilder tecnicas = new StringBuilder();
        resumen.tecnicas().forEach((nombre, cantidad) -> tecnicas.append("• ").append(nombre)
            .append(": ").append(cantidad).append('\n'));
        listaTecnicas.setText(tecnicas.length() == 0 ? "No se aplicaron transformaciones." : tecnicas.toString());
        listaTecnicas.setCaretPosition(0);

        modeloCambios.clear();
        cambiosActuales = nuevosCambios == null ? List.of() : List.copyOf(nuevosCambios);
        cambiosActuales.forEach(modeloCambios::addElement);
        reporte.setText(textoSeguro(textoReporte));
        reporte.setCaretPosition(0);
        if (!modeloCambios.isEmpty()) cambios.setSelectedIndex(0);
        else mostrarDetalle(null);
        habilitarAcciones(resumen.cambios() > 0 || resumen.lineasOriginales() > 0);
        tarjetas.revalidate();
        tarjetas.repaint();
        pestanas.setSelectedIndex(0);
    }

    public void poblar(ResultadoOptimizacion resultado) {
        if (resultado == null || resultado.metricas() == null) {
            limpiar();
            return;
        }
        MetricasOptimizacion m = resultado.metricas();
        Map<String, Integer> tecnicas = new LinkedHashMap<>();
        m.cambiosPorTecnica().forEach((tecnica, cantidad) ->
            tecnicas.put(tecnica.descripcion(), cantidad));
        List<CambioVista> vistas = resultado.cambios().stream().map(PanelOptimizacion::vista).toList();
        poblar(new ResumenVista(m.lineasOriginales(), m.lineasOptimizadas(), m.bytesOriginales(),
            m.bytesOptimizados(), vistas.size(), m.tiempoNanosegundos() / 1_000_000.0,
            m.cuadruplosOriginales(), m.cuadruplosOptimizados(), tecnicas),
            vistas, resultado.reporteTexto());
        comparar.setEnabled(resultado.exitoso());
        restaurar.setEnabled(resultado.exitoso());
        guardar.setEnabled(resultado.exitoso());
        exportar.setEnabled(true);
    }

    public void limpiar() {
        poblar(new ResumenVista(0, 0, 0, 0, 0, 0, 0, 0, new LinkedHashMap<>()),
            List.of(), "Todavía no se ha ejecutado la optimización.");
    }

    public void mostrarCambios() {
        pestanas.setSelectedIndex(1);
    }

    public void seleccionarCambio(int indice) {
        if (indice >= 0 && indice < modeloCambios.size()) {
            cambios.setSelectedIndex(indice);
            cambios.ensureIndexIsVisible(indice);
            mostrarCambios();
        }
    }

    public void addSeleccionCambioListener(Consumer<CambioVista> listener) {
        listenersSeleccion.add(listener);
    }

    public void configurarAcciones(Runnable verComparacion, Runnable restaurarOriginal,
            Runnable guardarOptimizado, Runnable exportarReporte) {
        accionComparar = verComparacion;
        accionRestaurar = restaurarOriginal;
        accionGuardar = guardarOptimizado;
        accionExportar = exportarReporte;
    }

    public String getReporte() {
        return reporte.getText();
    }

    public List<CambioVista> getCambios() {
        return cambiosActuales;
    }

    private void mostrarDetalle(CambioVista cambio) {
        if (cambio == null) {
            tituloDetalle.setText("Sin cambios para mostrar");
            ubicacionDetalle.setText("");
            explicacion.setText("El optimizador no encontró transformaciones seguras aplicables.");
            antes.setText("");
            despues.setText("");
            return;
        }
        tituloDetalle.setText("Cambio " + cambio.numero() + " · " + cambio.tecnica());
        ubicacionDetalle.setText("Original: " + cambio.ubicacionOriginal()
            + "   |   Optimizado: " + cambio.ubicacionOptimizada());
        explicacion.setText(cambio.explicacion());
        antes.setText(cambio.antes());
        despues.setText(cambio.despues().isBlank() ? "∅ Código eliminado" : cambio.despues());
        explicacion.setCaretPosition(0);
        antes.setCaretPosition(0);
        despues.setCaretPosition(0);
        listenersSeleccion.forEach(listener -> listener.accept(cambio));
    }

    private static JPanel tarjeta(String titulo, String valor, Color color) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(TemaFisio.BG_EDITOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(TemaFisio.BORDER),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JLabel nombre = etiqueta(titulo, Font.BOLD, 10, TemaFisio.FG_DIM);
        JLabel dato = etiqueta(valor, Font.BOLD, 15, color);
        panel.add(nombre, BorderLayout.NORTH);
        panel.add(dato, BorderLayout.CENTER);
        return panel;
    }

    private void habilitarAcciones(boolean habilitar) {
        comparar.setEnabled(habilitar);
        restaurar.setEnabled(habilitar);
        guardar.setEnabled(habilitar);
        exportar.setEnabled(habilitar);
    }

    private static void ejecutar(Runnable accion) {
        if (accion != null) accion.run();
    }

    private static JButton botonAccion(String texto) {
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

    private static JPanel base(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(TemaFisio.BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private static JLabel etiqueta(String texto, int estilo, int tamano, Color color) {
        JLabel label = new JLabel(texto);
        label.setFont(TemaFisio.fuenteUi(estilo, tamano));
        label.setForeground(color);
        return label;
    }

    private static JTextArea areaLectura(boolean ajustar) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(ajustar);
        area.setWrapStyleWord(ajustar);
        area.setBackground(TemaFisio.BG_EDITOR);
        area.setForeground(TemaFisio.FG_MAIN);
        area.setCaretColor(TemaFisio.ACCENT);
        area.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return area;
    }

    private static JTextArea areaCodigo() {
        JTextArea area = areaLectura(false);
        area.setFont(TemaFisio.fuenteMono(Font.PLAIN, 12));
        return area;
    }

    private static JScrollPane scroll(Component componente) {
        JScrollPane scroll = new JScrollPane(componente);
        scroll.setBorder(BorderFactory.createLineBorder(TemaFisio.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static String formatoBytes(long bytes) {
        return bytes < 1024 ? bytes + " B" : String.format("%.1f KB", bytes / 1024.0);
    }

    private static String porcentaje(long original, long optimizado) {
        if (original <= 0) return "0.0 %";
        return String.format("%.1f %%", Math.max(0, (original - optimizado) * 100.0 / original));
    }

    private static String textoSeguro(String texto) {
        return texto == null ? "" : texto;
    }

    private final class RenderCambio extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                boolean seleccionado, boolean foco) {
            JLabel label = (JLabel) super.getListCellRendererComponent(lista, valor, indice, seleccionado, foco);
            CambioVista cambio = (CambioVista) valor;
            label.setText("<html><b>Cambio " + cambio.numero() + " · " + escapar(cambio.tecnica())
                + "</b><br><span style='color:#a6adc8'>" + escapar(cambio.ubicacionOriginal()) + "</span></html>");
            label.setFont(TemaFisio.fuenteUi(Font.PLAIN, 12));
            label.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            return label;
        }
    }

    private static String escapar(String texto) {
        return textoSeguro(texto).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static CambioVista vista(CambioOptimizacion cambio) {
        return new CambioVista(cambio.numero(), cambio.tecnica().descripcion(),
            cambio.lineaOriginalInicio(), cambio.lineaOriginalFin(),
            cambio.lineaOptimizadaInicio(), cambio.lineaOptimizadaFin(),
            cambio.antes(), cambio.despues(), cambio.explicacion());
    }

}
