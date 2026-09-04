package fisio.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import fisio.token.CategoriaToken;
import fisio.token.TokenFisio;

/**
 * Tabla de tokens, equivalente visual a TablaTokens (gui.py / ttk.Treeview).
 *
 * Desde el Paso 6C, poblar(tokens) consume ResultadoLexico.tokens() --
 * incluye tambien los TokenFisio con categoria=ERROR (paridad con Python,
 * donde self._tokens ya trae esas entradas: ver TablaTokens.poblar() en
 * gui.py, que decide la fila "ERROR" mirando tok.tipo == TipoToken.ERROR
 * dentro de la MISMA lista de tokens, no de una lista de errores aparte).
 */
public final class TablaTokensPanel extends JPanel {

    private static final String[] COLUMNAS = {"Lexema", "Tipo", "ID Token", "Línea", "Columna"};
    private static final int[] ANCHOS = {180, 90, 90, 60, 70};

    private static final Color ERROR_FG = Color.decode("#ffffff");
    private static final Color ERROR_BG = Color.decode("#6b1f2b");

    private final DefaultTableModel modelo;
    private final JTable tabla;
    private List<TokenFisio> tokensActuales = List.of();

    public TablaTokensPanel() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);

        modelo = new DefaultTableModel(COLUMNAS, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };

        tabla = new JTable(modelo);
        tabla.setBackground(TemaFisio.BG_PANEL);
        tabla.setForeground(TemaFisio.FG_MAIN);
        tabla.setFont(TemaFisio.fuenteMono(Font.PLAIN, 11));
        tabla.setRowHeight(24);
        tabla.setGridColor(TemaFisio.BORDER);
        tabla.setSelectionBackground(TemaFisio.BORDER);
        tabla.setSelectionForeground(TemaFisio.FG_MAIN);
        tabla.getTableHeader().setBackground(TemaFisio.BG_TOOLBAR);
        tabla.getTableHeader().setForeground(TemaFisio.ACCENT);
        tabla.getTableHeader().setFont(TemaFisio.fuenteUi(Font.BOLD, 10));
        tabla.setDefaultRenderer(Object.class, new RenderizadorFila());

        for (int i = 0; i < ANCHOS.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(ANCHOS[i]);
        }

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.getViewport().setBackground(TemaFisio.BG_PANEL);
        add(scroll, BorderLayout.CENTER);
    }

    /** Reconstruye la tabla, una fila por TokenFisio, en el mismo orden. */
    public void poblar(List<TokenFisio> tokens) {
        tokensActuales = new ArrayList<>(tokens);
        modelo.setRowCount(0);
        for (TokenFisio tok : tokens) {
            modelo.addRow(new Object[]{
                tok.lexema(), tok.categoria().name(), tok.idFisio(), tok.linea(), tok.columna()
            });
        }
    }

    /** Deja la tabla sin filas. */
    public void limpiar() {
        tokensActuales = List.of();
        modelo.setRowCount(0);
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

    /**
     * Reproduce el esquema de colores de gui.py: filas ERROR con fondo
     * rojo oscuro/texto blanco fijo (ver tag_configure("ERROR", ...) en
     * TablaTokens); filas normales con texto del color de su categoria y
     * fondo alternado par/impar (bg_row_alt / bg_panel).
     */
    private final class RenderizadorFila extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object valor, boolean seleccionada, boolean foco, int fila, int columna) {
            JLabel etiqueta = (JLabel) super.getTableCellRendererComponent(
                t, valor, false, foco, fila, columna);

            CategoriaToken categoria = fila < tokensActuales.size()
                ? tokensActuales.get(fila).categoria() : CategoriaToken.ID;
            boolean esError = categoria == CategoriaToken.ERROR;

            if (esError) {
                etiqueta.setForeground(ERROR_FG);
                etiqueta.setBackground(ERROR_BG);
            } else {
                etiqueta.setForeground(colorDeCategoria(categoria));
                etiqueta.setBackground(fila % 2 == 0 ? TemaFisio.BG_ROW_ALT : TemaFisio.BG_PANEL);
            }

            if (seleccionada) {
                etiqueta.setBackground(TemaFisio.BORDER);
            }

            etiqueta.setHorizontalAlignment(columna == 0 ? JLabel.LEFT : JLabel.CENTER);
            return etiqueta;
        }
    }
}
