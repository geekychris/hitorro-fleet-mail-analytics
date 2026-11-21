import java.sql.*;
/** Prints every row of a table as a JSON object of column -> string value (or null), ordered by column 1. */
public class H2Dump {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection(a[0], "sa", ""); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM " + a[1] + " ORDER BY 1")) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                StringBuilder b = new StringBuilder("{");
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    if (i > 1) b.append(',');
                    b.append(q(md.getColumnLabel(i).toLowerCase())).append(':');
                    String v = rs.getString(i);
                    b.append(v == null ? "null" : q(v));
                }
                System.out.println(b.append('}'));
            }
        }
    }
    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch); }
            }
        }
        return b.append('"').toString();
    }
}
