import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class FacebookDataToPostgres {

    static final String DB_URL = "jdbc:postgresql://localhost:5433/vinany";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "harenafinaritra";

    static final Map<String, String> TYPES_BRUTES = Map.ofEntries(
            Map.entry("longueur_message", "INTEGER"),
            Map.entry("nb_hashtags", "INTEGER"),
            Map.entry("nb_tags", "INTEGER"),
            Map.entry("contient_lien_externe", "BOOLEAN"),
            Map.entry("contient_emoji", "BOOLEAN"),
            Map.entry("contient_question", "BOOLEAN"),
            Map.entry("nb_photos_album", "INTEGER"),
            Map.entry("duree_video", "DOUBLE"),
            Map.entry("longueur_titre_video", "INTEGER"),
            Map.entry("heure_publication", "INTEGER"),
            Map.entry("jours_depuis_dernier_post", "INTEGER"),
            Map.entry("nb_posts_7j", "INTEGER"),
            Map.entry("nb_posts_30j", "INTEGER"),
            Map.entry("vues", "DOUBLE"),
            Map.entry("reactions", "DOUBLE"),
            Map.entry("commentaires", "INTEGER")
    );

    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

        importerCsv(conn, "output/features_facebook.csv", "donnees_facebook_brutes", false);
        importerCsv(conn, "output/features_facebook_encoded.csv", "donnees_facebook_encoded", true);

        conn.close();
        System.out.println("Import terminé.");
    }

    static void viderTable(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            System.out.println("Table " + table + " vidée.");
        }
    }

    static void importerCsv(Connection conn, String fichierCsv, String table, boolean numerique) throws Exception {
        viderTable(conn, table);

        try (BufferedReader reader = new BufferedReader(new FileReader(fichierCsv, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                System.out.println("Fichier vide : " + fichierCsv);
                return;
            }
            List<String> headers = splitCsvLine(headerLine);

            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < headers.size(); i++) {
                placeholders.append(i == 0 ? "?" : ", ?");
            }
            String sql = "INSERT INTO " + table + " (" + String.join(", ", headers) + ") VALUES (" + placeholders + ")";
            PreparedStatement stmt = conn.prepareStatement(sql);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                List<String> values = splitCsvLine(line);

                for (int i = 0; i < headers.size(); i++) {
                    String val = i < values.size() ? values.get(i) : "";
                    if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1).replace("\"\"", "\"");
                    }

                    String colonne = headers.get(i);

                    if (numerique) {
                        if (colonne.endsWith("_classe")) {
                            stmt.setString(i + 1, val);
                        } else if (val.isEmpty()) {
                            stmt.setNull(i + 1, Types.DOUBLE);
                        } else {
                            stmt.setDouble(i + 1, Double.parseDouble(val));
                        }
                    } else {
                        insererValeurTypee(stmt, i + 1, colonne, val);
                    }
                }
                stmt.executeUpdate();
                count++;
            }
            stmt.close();
            System.out.println(count + " lignes insérées dans " + table);
        }
    }

    static void insererValeurTypee(PreparedStatement stmt, int index, String colonne, String val) throws SQLException {
        String type = TYPES_BRUTES.getOrDefault(colonne, "TEXT");

        if (val == null || val.isEmpty()) {
            int sqlType = switch (type) {
                case "INTEGER" -> Types.INTEGER;
                case "DOUBLE" -> Types.DOUBLE;
                case "BOOLEAN" -> Types.BOOLEAN;
                default -> Types.VARCHAR;
            };
            stmt.setNull(index, sqlType);
            return;
        }

        switch (type) {
            case "INTEGER" -> stmt.setInt(index, (int) Double.parseDouble(val));
            case "DOUBLE" -> stmt.setDouble(index, Double.parseDouble(val));
            case "BOOLEAN" -> stmt.setBoolean(index, val.trim().equalsIgnoreCase("true"));
            default -> stmt.setString(index, val);
        }
    }

    static List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}