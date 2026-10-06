import weka.core.Instances;
import weka.core.Attribute;
import weka.core.converters.CSVLoader;
import weka.classifiers.trees.J48;
import weka.classifiers.Evaluation;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.instance.RemoveWithValues;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

public class WekaFacebookClassification {

    static final String DB_URL = "jdbc:postgresql://localhost:5433/vinany";
    static final String DB_USER = "postgres";
    static final String DB_PASSWORD = "harenafinaritra";

    static final String FICHIER = "output/features_facebook_encoded.csv";
    static final String[] CIBLES = {"reactions_classe", "commentaires_classe", "vues_classe"};

    static final boolean AFFICHER_REGLES = true;

    static final List<String> COLONNES_RESULTATS = List.of(
            "vues", "reactions", "commentaires",
            "vues_classe", "reactions_classe", "commentaires_classe"
    );

    public static void main(String[] args) throws Exception {
        Logger.getLogger("").setLevel(Level.SEVERE);

        CSVLoader loader = new CSVLoader();
        loader.setNominalAttributes(indicesColonnesClasse(FICHIER));
        loader.setSource(new File(FICHIER));
        Instances data = loader.getDataSet();

        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

        for (String cible : CIBLES) {
            Instances dataCible = new Instances(data);

            if (cible.equals("vues_classe")) {
                dataCible = retirerNonApplicable(dataCible, cible);
            }
            dataCible = retirerAutresCibles(dataCible, cible);
            dataCible.setClass(dataCible.attribute(cible));

            dataCible.randomize(new Random(1));
            int trainSize = (int) Math.round(dataCible.numInstances() * 0.8);
            int testSize = dataCible.numInstances() - trainSize;
            Instances train = new Instances(dataCible, 0, trainSize);
            Instances test = new Instances(dataCible, trainSize, testSize);

            entrainerEtStocker("facebook_j48_" + cible, cible, train, test, conn);
        }

        conn.close();
        System.out.println("\nModèles stockés.");
    }

    static String indicesColonnesClasse(String fichier) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(fichier, StandardCharsets.UTF_8))) {
            String[] colonnes = reader.readLine().split(",");
            StringBuilder indices = new StringBuilder();
            for (int i = 0; i < colonnes.length; i++) {
                if (colonnes[i].trim().endsWith("_classe")) {
                    if (indices.length() > 0) indices.append(",");
                    indices.append(i + 1);
                }
            }
            return indices.toString();
        }
    }

    static Instances retirerNonApplicable(Instances data, String cible) throws Exception {
        RemoveWithValues filtre = new RemoveWithValues();
        int index = data.attribute(cible).index() + 1;
        int valeur = data.attribute(cible).indexOfValue("non_applicable") + 1;
        filtre.setOptions(weka.core.Utils.splitOptions("-C " + index + " -L " + valeur));
        filtre.setInputFormat(data);
        return Filter.useFilter(data, filtre);
    }

    static Instances retirerAutresCibles(Instances data, String cibleAGarder) throws Exception {
        StringBuilder indices = new StringBuilder();
        for (String col : COLONNES_RESULTATS) {
            if (col.equals(cibleAGarder)) continue;
            Attribute attr = data.attribute(col);
            if (attr != null) {
                if (indices.length() > 0) indices.append(",");
                indices.append(attr.index() + 1);
            }
        }
        if (indices.length() == 0) return data;

        Remove filtre = new Remove();
        filtre.setOptions(weka.core.Utils.splitOptions("-R " + indices));
        filtre.setInputFormat(data);
        return Filter.useFilter(data, filtre);
    }

    static String extraireRegles(String arbre) {
        int debut = arbre.indexOf("------------------");
        int fin = arbre.indexOf("Number of Leaves");
        if (debut < 0 || fin < 0) return arbre;
        return arbre.substring(arbre.indexOf('\n', debut) + 1, fin).trim();
    }

    static void entrainerEtStocker(String nom, String cible, Instances train, Instances test,
                                    Connection conn) throws Exception {
        J48 modele = new J48();
        modele.setMinNumObj(5);
        modele.buildClassifier(train);

        Evaluation eval = new Evaluation(train);
        eval.evaluateModel(modele, test);

        double accuracy = eval.pctCorrect();

        System.out.printf("%n%s : %.0f%% (%d/%d bien classés)%n",
                cible, accuracy, (int) eval.correct(), test.numInstances());
        if (AFFICHER_REGLES) {
            System.out.println(extraireRegles(modele.toString()));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(modele);
        oos.close();

        String sql = "INSERT INTO modeles_classification_facebook " +
                "(nom_modele, variable_cible, accuracy, modele_binaire) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, nom);
        stmt.setString(2, cible);
        stmt.setDouble(3, accuracy);
        stmt.setBytes(4, baos.toByteArray());
        stmt.executeUpdate();
        stmt.close();
    }
}