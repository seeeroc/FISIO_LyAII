package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import fisio.token.CategoriaToken;
import fisio.token.ErrorLexico;
import fisio.token.TokenFisio;

/**
 * Panel de resumen/estadisticas, equivalente a PanelResumen (gui.py).
 *
 * Datos reproducidos (unicamente los que gui.py realmente calcula en
 * poblar(tokens, errores) -- ninguna metrica adicional): total de tokens
 * (incluye las entradas categoria=ERROR, igual que Python), total de
 * errores, estado exitoso/con errores, distribucion por categoria con
 * barra proporcional al maximo, y el detalle linea/columna/lexema/mensaje
 * de cada error.
 */
public final class PanelResumen extends JPanel {

    private static final int ANCHO_BARRA = 200;
    private static final int ALTO_BARRA = 14;

    // Mismo orden/etiquetas que PanelResumen.TIPOS en gui.py.
    private static final List<Map.Entry<CategoriaToken, String>> TIPOS = List.of(
        Map.entry(CategoriaToken.PR, "Palabras Reservadas"),
        Map.entry(CategoriaToken.OPM, "Op. Matemáticos"),
        Map.entry(CategoriaToken.OPR, "Op. Relacionales"),
        Map.entry(CategoriaToken.SIG, "Signos"),
        Map.entry(CategoriaToken.ID, "Identificadores"),
        Map.entry(CategoriaToken.NUM, "Números"),
        Map.entry(CategoriaToken.ERROR, "Errores léxicos")
    );

    private final JPanel contenido;

    public PanelResumen() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);

        contenido = new JPanel();
        contenido.setBackground(TemaFisio.BG_PANEL);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(contenido);
        scroll.getViewport().setBackground(TemaFisio.BG_PANEL);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    public void poblar(List<TokenFisio> tokens, List<ErrorLexico> errores) {
        contenido.removeAll();

        int total = tokens.size();
        int nErr = errores.size();
        Map<CategoriaToken, Integer> conteos = new LinkedHashMap<>();
        for (TokenFisio tok : tokens) {
            conteos.merge(tok.categoria(), 1, Integer::sum);
        }

        agregarTitulo("RESUMEN DEL ANÁLISIS");
        agregarSeparador();

        Color colorErr = nErr == 0 ? TemaFisio.OK_GREEN : TemaFisio.COL_ERROR;
        String estadoTxt = nErr == 0 ? "ANÁLISIS EXITOSO" : "CON ERRORES (" + nErr + ")";
        Color estadoCol = nErr == 0 ? TemaFisio.OK_GREEN : TemaFisio.COL_ERROR;

        agregarFilaEstadistica("Total tokens:", String.valueOf(total), TemaFisio.COL_NUM);
        agregarFilaEstadistica("Total errores:", String.valueOf(nErr), colorErr);
        agregarFilaEstadistica("Estado:", estadoTxt, estadoCol);

        agregarSeparador();

        JLabel lblDistribucion = new JLabel("Distribución por tipo:");
        lblDistribucion.setForeground(TemaFisio.FG_MAIN);
        lblDistribucion.setFont(TemaFisio.fuenteUi(Font.BOLD, 11));
        lblDistribucion.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblDistribucion.setBorder(BorderFactory.createEmptyBorder(0, 20, 6, 0));
        contenido.add(lblDistribucion);

        int maxVal = 1;
        for (int v : conteos.values()) {
            maxVal = Math.max(maxVal, v);
        }

        for (Map.Entry<CategoriaToken, String> tipo : TIPOS) {
            int cnt = conteos.getOrDefault(tipo.getKey(), 0);
            Color color = colorDeCategoria(tipo.getKey());
            double pct = maxVal > 0 ? (double) cnt / maxVal : 0;
            agregarFilaDistribucion(tipo.getKey().name(), tipo.getValue(), color, cnt, pct);
        }

        if (!errores.isEmpty()) {
            agregarSeparador();
            JLabel lblErrores = new JLabel("Errores detectados:");
            lblErrores.setForeground(TemaFisio.COL_ERROR);
            lblErrores.setFont(TemaFisio.fuenteUi(Font.BOLD, 11));
            lblErrores.setAlignmentX(Component.LEFT_ALIGNMENT);
            lblErrores.setBorder(BorderFactory.createEmptyBorder(0, 20, 4, 0));
            contenido.add(lblErrores);

            for (ErrorLexico err : errores) {
                agregarFilaError(err);
            }
        }

        contenido.revalidate();
        contenido.repaint();
    }

    /** Sin contenido dinamico hasta que poblar() se invoque. */
    public void limpiar() {
        contenido.removeAll();
        contenido.revalidate();
        contenido.repaint();
    }

    private void agregarTitulo(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(TemaFisio.ACCENT);
        lbl.setFont(TemaFisio.fuenteUi(Font.BOLD, 13));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(16, 20, 2, 0));
        contenido.add(lbl);
    }

    private void agregarSeparador() {
        JPanel linea = new JPanel();
        linea.setBackground(TemaFisio.BORDER);

        JPanel envoltura = new JPanel(new BorderLayout());
        envoltura.setBackground(TemaFisio.BG_PANEL);
        envoltura.setAlignmentX(Component.LEFT_ALIGNMENT);
        envoltura.setBorder(BorderFactory.createEmptyBorder(4, 20, 6, 20));
        envoltura.add(linea, BorderLayout.CENTER);
        envoltura.setMaximumSize(new Dimension(Short.MAX_VALUE, 11));
        envoltura.setPreferredSize(new Dimension(10, 11));
        contenido.add(envoltura);
    }

    private void agregarFilaEstadistica(String etiqueta, String valor, Color color) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        fila.setBackground(TemaFisio.BG_PANEL);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        JLabel lblEtiqueta = new JLabel(etiqueta);
        lblEtiqueta.setForeground(TemaFisio.FG_DIM);
        lblEtiqueta.setFont(TemaFisio.fuenteUi(Font.PLAIN, 11));
        lblEtiqueta.setPreferredSize(new Dimension(140, 18));

        JLabel lblValor = new JLabel(valor);
        lblValor.setForeground(color);
        lblValor.setFont(TemaFisio.fuenteMono(Font.BOLD, 12));

        fila.add(lblEtiqueta);
        fila.add(lblValor);
        contenido.add(fila);
    }

    private void agregarFilaDistribucion(String sigla, String nombre, Color color, int cnt, double pct) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        fila.setBackground(TemaFisio.BG_PANEL);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));

        JLabel lblSigla = new JLabel(sigla);
        lblSigla.setForeground(color);
        lblSigla.setFont(TemaFisio.fuenteMono(Font.BOLD, 11));
        lblSigla.setPreferredSize(new Dimension(55, 18));

        JLabel lblNombre = new JLabel(nombre);
        lblNombre.setForeground(TemaFisio.FG_DIM);
        lblNombre.setFont(TemaFisio.fuenteUi(Font.PLAIN, 10));
        lblNombre.setPreferredSize(new Dimension(130, 18));

        JPanel pista = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pista.setBackground(TemaFisio.BG_TOOLBAR);
        pista.setPreferredSize(new Dimension(ANCHO_BARRA, ALTO_BARRA));

        int anchoRelleno = cnt > 0 ? Math.max((int) (ANCHO_BARRA * pct), 2) : 0;
        if (anchoRelleno > 0) {
            JPanel relleno = new JPanel();
            relleno.setBackground(color);
            relleno.setPreferredSize(new Dimension(anchoRelleno, ALTO_BARRA));
            pista.add(relleno);
        }

        JLabel lblCnt = new JLabel(String.valueOf(cnt));
        lblCnt.setForeground(color);
        lblCnt.setFont(TemaFisio.fuenteMono(Font.BOLD, 11));
        lblCnt.setPreferredSize(new Dimension(30, 18));
        lblCnt.setHorizontalAlignment(JLabel.RIGHT);

        fila.add(lblSigla);
        fila.add(lblNombre);
        fila.add(pista);
        fila.add(lblCnt);
        contenido.add(fila);
    }

    private void agregarFilaError(ErrorLexico err) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        fila.setBackground(TemaFisio.BG_PANEL);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));

        JLabel lblPos = new JLabel(String.format("Línea %3d, Col %3d |", err.linea(), err.columna()));
        lblPos.setForeground(TemaFisio.FG_DIM);
        lblPos.setFont(TemaFisio.fuenteMono(Font.PLAIN, 10));

        JLabel lblLexema = new JLabel(" '" + err.lexema() + "'");
        lblLexema.setForeground(TemaFisio.COL_OPR);
        lblLexema.setFont(TemaFisio.fuenteMono(Font.BOLD, 10));

        JLabel lblMensaje = new JLabel("  " + err.mensaje());
        lblMensaje.setForeground(TemaFisio.COL_ERROR);
        lblMensaje.setFont(TemaFisio.fuenteUi(Font.PLAIN, 9));

        fila.add(lblPos);
        fila.add(lblLexema);
        fila.add(lblMensaje);
        contenido.add(fila);
    }

    private Color colorDeCategoria(CategoriaToken categoria) {
        return switch (categoria) {
            case PR -> TemaFisio.COL_PR;
            case OPM -> TemaFisio.COL_OPM;
            case OPR -> TemaFisio.COL_OPR;
            case SIG -> TemaFisio.COL_SIG;
            case ID -> TemaFisio.COL_ID;
            case NUM -> TemaFisio.COL_NUM;
            case ERROR -> TemaFisio.COL_ERROR;
            case EOF -> TemaFisio.FG_MAIN;
        };
    }
}
