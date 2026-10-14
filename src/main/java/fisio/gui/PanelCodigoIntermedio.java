package fisio.gui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import fisio.ci.ExpresionIntermedia;
import fisio.ci.ResultadoCodigoIntermedio;

/** Presenta las representaciones y la resolución de cada expresión aritmética. */
public final class PanelCodigoIntermedio extends JPanel {
    private final JEditorPane documento = new JEditorPane("text/html", "");

    public PanelCodigoIntermedio() {
        super(new BorderLayout());
        setBackground(TemaFisio.BG_PANEL);
        documento.setEditable(false);
        documento.setBackground(TemaFisio.BG_PANEL);
        documento.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane scroll = new JScrollPane(documento);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        limpiar();
    }

    public void poblar(ResultadoCodigoIntermedio resultado) {
        if (!resultado.generado()) {
            documento.setText(pagina("<h1>CÓDIGO INTERMEDIO NO GENERADO</h1><p>" + escapar(resultado.motivo()) + "</p>"));
        } else if (resultado.expresiones().isEmpty()) {
            documento.setText(pagina("<h1>CÓDIGO INTERMEDIO</h1><div class='aviso'>"
                + "No se encontraron expresiones aritméticas para procesar.</div>"));
        } else {
            StringBuilder html = new StringBuilder("<h1>CÓDIGO INTERMEDIO · EXPRESIONES ARITMÉTICAS</h1>");
            int numero = 1;
            for (ExpresionIntermedia expresion : resultado.expresiones()) {
                agregarExpresion(html, numero++, expresion);
            }
            documento.setText(pagina(html.toString()));
        }
        documento.setCaretPosition(0);
    }

    private static void agregarExpresion(StringBuilder html, int numero, ExpresionIntermedia expresion) {
        html.append("<section><h2>Expresión ").append(numero).append(" · ")
            .append(escapar(expresion.contexto())).append("</h2>")
            .append("<p class='ubicacion'>Línea ").append(expresion.linea()).append(", columna ")
            .append(expresion.columna()).append("</p>")
            .append("<h3>Expresión original</h3><code>").append(escapar(expresion.infija())).append("</code>")
            .append("<h3>Conversión de expresiones</h3>")
            .append("<p><b>Postfija:</b> <code>").append(escapar(expresion.postfija())).append("</code></p>")
            .append("<p><b>Prefija:</b> <code>").append(escapar(expresion.prefija())).append("</code></p>")
            .append("<h3>Tabla de cuádruplos</h3>")
            .append("<table><tr><th>N.º</th><th>Operador</th><th>Argumento 1</th><th>Argumento 2</th><th>Resultado</th></tr>");
        expresion.cuadruplos().forEach(c -> html.append("<tr><td>").append(c.numero()).append("</td><td>")
            .append(escapar(c.operador())).append("</td><td>").append(escapar(c.argumento1()))
            .append("</td><td>").append(c.argumento2().isBlank() ? "—" : escapar(c.argumento2()))
            .append("</td><td>").append(escapar(c.resultado())).append("</td></tr>"));
        html.append("</table><h3>Resolución paso a paso</h3>");
        expresion.pasos().forEach(p -> {
            int separador = p.operacion().indexOf(" = ");
            String temporal = separador < 0 ? "temporal" : p.operacion().substring(0, separador);
            html.append("<div class='paso'><b>Paso ").append(p.numero()).append(". Generar ")
                .append(escapar(temporal)).append("</b><br><code>").append(escapar(p.operacion()))
                .append("</code><br>").append(escapar(p.detalle())).append("</div>");
        });
        html.append("<h3>Resultado final</h3><div class='")
            .append(expresion.resultadoDisponible() ? "resultado" : "aviso").append("'>")
            .append(expresion.resultadoDisponible() ? "<b>" + escapar(expresion.resultadoFinal()) + "</b><br>" : "")
            .append(escapar(expresion.detalleResultado())).append("</div></section>");
    }

    private static String pagina(String contenido) {
        return "<html><head><style>"
            + "body{background:#11111b;color:#cdd6f4;font-family:'Segoe UI',sans-serif;margin:18px;}"
            + "h1{font-size:17px;color:#89b4fa;} h2{font-size:16px;color:#cba6f7;margin-top:7px;}"
            + "h3{font-size:13px;color:#89dceb;margin:16px 0 7px;} p{margin:6px 0;}"
            + "section{border:1px solid #45475a;padding:13px;margin:0 0 18px;}"
            + "code{font-family:Consolas,monospace;color:#f9e2af;} .ubicacion{color:#a6adc8;}"
            + "table{border-collapse:collapse;width:100%;font-family:Consolas,monospace;}"
            + "th,td{border:1px solid #45475a;padding:6px;text-align:left;} th{background:#313244;color:#89b4fa;}"
            + ".paso{background:#181825;border-left:3px solid #89b4fa;padding:8px;margin:7px 0;}"
            + ".resultado{background:#18321f;color:#a6e3a1;padding:9px;}"
            + ".aviso{background:#30261b;color:#f9e2af;padding:9px;}"
            + "</style></head><body>" + contenido + "</body></html>";
    }

    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public void limpiar() {
        documento.setText(pagina(""));
    }
}
