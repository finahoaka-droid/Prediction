import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

public class FacebookPredictor {

    static final String DB_URL = "jdbc:postgresql://localhost:5433/vinany";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "harenafinaritra";

    static final String FICHIER = "output/features_facebook_encoded.csv";
    static final String[] CIBLES = {"reactions_classe", "commentaires_classe", "vues_classe"};

    static Classifier chargerModele(Connection conn, String cible) throws Exception {
        String sql = "SELECT modele_binaire FROM modeles_classification_facebook " +
                "WHERE variable_cible = ? ORDER BY date_entrainement DESC, id DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cible);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) throw new IllegalStateException("Aucun modèle pour " + cible);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(rs.getBytes(1)))) {
                return (Classifier) ois.readObject();
            }
        }
    }

    static Instances construireEntete(String cible) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setNominalAttributes(WekaFacebookClassification.indicesColonnesClasse(FICHIER));
        loader.setSource(new File(FICHIER));
        Instances data = WekaFacebookClassification.retirerAutresCibles(loader.getDataSet(), cible);
        data.setClass(data.attribute(cible));
        return new Instances(data, 0);
    }

    static void predire(String titre, Classifier modele, Instances entete, Map<String, String> encode) throws Exception {
        Instance inst = new DenseInstance(entete.numAttributes());
        inst.setDataset(entete);
        for (int i = 0; i < entete.numAttributes(); i++) {
            String val = encode.get(entete.attribute(i).name());
            if (i == entete.classIndex()) inst.setMissing(i);
            else inst.setValue(i, val == null || val.isEmpty() ? 0 : Double.parseDouble(val));
        }

        double[] dist = modele.distributionForInstance(inst);
        int best = 0;
        for (int i = 1; i < dist.length; i++) if (dist[i] > dist[best]) best = i;

        System.out.printf("%n%s : %s  (confiance %.0f%%)%n", titre, entete.classAttribute().value(best), dist[best] * 100);
        for (int i = 0; i < dist.length; i++) {
            String classe = entete.classAttribute().value(i);
            if (!classe.equals("non_applicable")) System.out.printf("    %-8s %5.1f%%%n", classe, dist[i] * 100);
        }
    }

    public static void main(String[] args) throws Exception {
        Logger.getLogger("").setLevel(Level.SEVERE);

        Classifier[] modeles = new Classifier[CIBLES.length];
        Instances[] entetes = new Instances[CIBLES.length];
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            for (int i = 0; i < CIBLES.length; i++) {
                modeles[i] = chargerModele(conn, CIBLES[i]);
                entetes[i] = construireEntete(CIBLES[i]);
            }
        }
        System.out.println("Modèles chargés depuis la base.");

        Scanner sc = new Scanner(System.in);
        String encore = "oui";
        while (encore.equals("oui")) {

            System.out.println("\n=== Simulation d'une publication ===");
            Map<String, String> p = new LinkedHashMap<>();
            String type = choix(sc, "Type de post", FacebookEncoder.TYPE_POST_CATEGORIES, "photo");

            p.put("longueur_message", lire(sc, "Longueur du message (caractères)", "0"));
            p.put("nb_hashtags", lire(sc, "Nombre de hashtags", "0"));
            p.put("nb_tags", lire(sc, "Nombre de personnes/pages taguées", "0"));
            p.put("contient_lien_externe", lire(sc, "Contient un lien externe ? (oui/non)", "non"));
            p.put("contient_emoji", lire(sc, "Contient un emoji ? (oui/non)", "non"));
            p.put("contient_question", lire(sc, "Contient une question ? (oui/non)", "non"));
            p.put("type_post", type);
            p.put("ratio_image", type.equals("texte") || type.equals("evenement") ? ""
                    : lire(sc, "Ratio de l'image largeur:hauteur (ex 1:1, 16:9)", ""));
            p.put("nb_photos_album", type.equals("album") ? lire(sc, "Nombre de photos dans l'album", "2") : "");
            p.put("duree_video", type.equals("video") ? lire(sc, "Durée de la vidéo (secondes)", "30") : "");
            p.put("longueur_titre_video", type.equals("video") ? lire(sc, "Longueur du titre de la vidéo", "0") : "");
            p.put("jour_semaine", choix(sc, "Jour de publication", FacebookEncoder.JOUR_SEMAINE_CATEGORIES, "lundi"));
            p.put("heure_publication", lire(sc, "Heure de publication (0-23)", "12"));
            p.put("jours_depuis_dernier_post", lire(sc, "Jours depuis le dernier post", "1"));
            p.put("nb_posts_7j", lire(sc, "Nombre de posts sur les 7 derniers jours", "0"));
            p.put("nb_posts_30j", lire(sc, "Nombre de posts sur les 30 derniers jours", "0"));

            Map<String, String> encode = FacebookEncoder.encodeRow(p);
            System.out.println("\n=== Prédictions ===");
            predire("Réactions   ", modeles[0], entetes[0], encode);
            predire("Commentaires", modeles[1], entetes[1], encode);
            if (type.equals("video") || type.equals("album")) {
                predire("Vues        ", modeles[2], entetes[2], encode);
            } else {
                System.out.println("\nVues         : non applicable (seulement pour vidéo et album)");
            }

            encore = lire(sc, "\nSimuler une autre publication ? (oui/non)", "non");
        }
    }

    static String lire(Scanner sc, String question, String defaut) {
        System.out.print(question + (defaut.isEmpty() ? "" : " [" + defaut + "]") + " : ");
        String s = sc.nextLine().trim().toLowerCase();
        return s.isEmpty() ? defaut : s;
    }

    static String choix(Scanner sc, String question, List<String> options, String defaut) {
        String s;
        do s = lire(sc, question + " " + options, defaut);
        while (!options.contains(s));
        return s;
    }
}