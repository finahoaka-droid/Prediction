import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FacebookEncoder {

    static final String INPUT_FILE = "output/features_facebook.csv";
    static final String OUTPUT_FILE = "output/features_facebook_encoded.csv";

    static final List<String> TYPE_POST_CATEGORIES = List.of(
            "texte", "photo", "album", "video", "lien", "evenement"
    );
    static final String TYPE_POST_REFERENCE = "texte";

    static final List<String> JOUR_SEMAINE_CATEGORIES = List.of(
            "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"
    );
    static final String JOUR_SEMAINE_REFERENCE = "dimanche";

    static final List<String> BOOLEAN_COLUMNS = List.of(
            "contient_lien_externe", "contient_emoji", "contient_question"
    );

    static final List<String> NUMERIC_COLUMNS = List.of(
            "longueur_message", "nb_hashtags", "nb_tags",
            "nb_photos_album", "duree_video", "longueur_titre_video",
            "heure_publication",
            "jours_depuis_dernier_post", "nb_posts_7j", "nb_posts_30j",
            "vues", "reactions", "commentaires"
    );

    static String encodeRatioImage(String value) {
        if (value == null || value.isEmpty() || !value.contains(":")) return "0";
        try {
            String[] parts = value.split(":");
            double largeur = Double.parseDouble(parts[0]);
            double hauteur = Double.parseDouble(parts[1]);
            if (hauteur == 0 || largeur == 0) return "0";
            return String.valueOf(Math.log(largeur / hauteur));
        } catch (Exception e) {
            return "0";
        }
    }

    static List<Map<String, String>> readCsv(String filename) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;
            List<String> headers = splitCsvLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                List<String> values = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < values.size(); i++) {
                    row.put(headers.get(i), values.get(i));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    static List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
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

    static String encodeBoolean(String value) {
        if (value == null || value.isEmpty()) return "0";
        String v = value.trim().toLowerCase();
        return (v.equals("true") || v.equals("oui") || v.equals("1") || v.equals("yes")) ? "1" : "0";
    }

    static String toNumeric(String value) {
        if (value == null || value.isEmpty()) return "0";
        try {
            double d = Double.parseDouble(value);
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    static Map<String, String> encodeRow(Map<String, String> row) {
        Map<String, String> encoded = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : row.entrySet()) {
            String col = e.getKey();
            String val = e.getValue();

            if (col.equals("type_post")) {
                for (String cat : TYPE_POST_CATEGORIES) {
                    if (cat.equals(TYPE_POST_REFERENCE)) continue;
                    encoded.put("type_post_" + cat, val != null && val.equals(cat) ? "1" : "0");
                }

            } else if (col.equals("jour_semaine")) {
                String valLower = val == null ? "" : val.toLowerCase();
                for (String cat : JOUR_SEMAINE_CATEGORIES) {
                    if (cat.equals(JOUR_SEMAINE_REFERENCE)) continue;
                    encoded.put("jour_" + cat, valLower.equals(cat) ? "1" : "0");
                }

            } else if (col.equals("ratio_image")) {
                encoded.put("ratio_image", encodeRatioImage(val));

            } else if (BOOLEAN_COLUMNS.contains(col)) {
                encoded.put(col, encodeBoolean(val));

            } else if (NUMERIC_COLUMNS.contains(col)) {
                encoded.put(col, toNumeric(val));

            } else {
                encoded.put(col, val);
            }
        }
        return encoded;
    }

    static double percentile(List<Double> triee, double p) {
        int index = (int) Math.floor(p * (triee.size() - 1));
        return triee.get(index);
    }

    static String classifierReactions(double valeur, double seuil1, double seuil2) {
        long a = (long) seuil1;
        long b = (long) seuil2;
        if (valeur < seuil1) return "0-" + (a - 1);
        if (valeur < seuil2) return a + "-" + (b - 1);
        return b + "+";
    }

    static String classifierCommentaires(double valeur) {
        return valeur == 0 ? "0" : "1+";
    }

    static String classifierVues(double valeur, double mediane) {
        long m = (long) mediane;
        return valeur < m ? "0-" + (m - 1) : m + "+";
    }

    static void writeCsv(List<Map<String, String>> rows, String filename) throws IOException {
        if (rows.isEmpty()) {
            System.out.println("Aucune ligne à encoder.");
            return;
        }
        try (FileWriter writer = new FileWriter(filename, StandardCharsets.UTF_8)) {
            List<String> headers = new ArrayList<>(rows.get(0).keySet());
            writer.write(String.join(",", headers) + "\n");
            for (Map<String, String> row : rows) {
                List<String> values = new ArrayList<>();
                for (String h : headers) values.add(row.getOrDefault(h, ""));
                writer.write(String.join(",", values) + "\n");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        List<Map<String, String>> rawRows = readCsv(INPUT_FILE);
        System.out.println(rawRows.size() + " lignes chargées depuis " + INPUT_FILE);

        List<Map<String, String>> encodedRows = new ArrayList<>();
        for (Map<String, String> row : rawRows) {
            encodedRows.add(encodeRow(row));
        }
        List<Double> reactionsTriees = new ArrayList<>();
        for (Map<String, String> row : encodedRows) reactionsTriees.add(Double.parseDouble(row.get("reactions")));
        reactionsTriees.sort(Double::compareTo);
        double seuilReactions1 = percentile(reactionsTriees, 0.33);
        double seuilReactions2 = percentile(reactionsTriees, 0.66);

        List<Double> vuesTriees = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            String vuesRaw = rawRows.get(i).get("vues");
            if (vuesRaw != null && !vuesRaw.isEmpty()) {
                vuesTriees.add(Double.parseDouble(encodedRows.get(i).get("vues")));
            }
        }
        vuesTriees.sort(Double::compareTo);
        double medianeVues = vuesTriees.isEmpty() ? 0 : percentile(vuesTriees, 0.5);

        System.out.println("Seuils reactions : faible < " + seuilReactions1 + " < moyen < " + seuilReactions2 + " < eleve");
        System.out.println("Seuil vues (médiane, " + vuesTriees.size() + " posts vidéo/album) : " + medianeVues);

        for (int i = 0; i < encodedRows.size(); i++) {
            Map<String, String> row = encodedRows.get(i);

            double reactions = Double.parseDouble(row.get("reactions"));
            row.put("reactions_classe", classifierReactions(reactions, seuilReactions1, seuilReactions2));

            double commentaires = Double.parseDouble(row.get("commentaires"));
            row.put("commentaires_classe", classifierCommentaires(commentaires));

            String vuesRaw = rawRows.get(i).get("vues");
            if (vuesRaw != null && !vuesRaw.isEmpty()) {
                double vues = Double.parseDouble(row.get("vues"));
                row.put("vues_classe", classifierVues(vues, medianeVues));
            } else {
                row.put("vues_classe", "non_applicable");
            }
        }

        writeCsv(encodedRows, OUTPUT_FILE);
        System.out.println("Fichier encodé sauvegardé : " + OUTPUT_FILE);
        if (!encodedRows.isEmpty()) {
            System.out.println("Colonnes finales : " + encodedRows.get(0).keySet());
        }
    }
}