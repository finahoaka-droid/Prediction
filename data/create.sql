CREATE DATABASE vinany;
\c vinany;

CREATE TABLE donnees_facebook_brutes (
    id SERIAL PRIMARY KEY,
    longueur_message INTEGER,
    nb_hashtags INTEGER,
    nb_tags INTEGER,
    contient_lien_externe BOOLEAN,
    contient_emoji BOOLEAN,
    contient_question BOOLEAN,
    type_post TEXT,
    ratio_image TEXT,
    nb_photos_album INTEGER,
    duree_video DOUBLE PRECISION,
    longueur_titre_video INTEGER,
    jour_semaine TEXT,
    heure_publication INTEGER,
    jours_depuis_dernier_post INTEGER,
    nb_posts_7j INTEGER,
    nb_posts_30j INTEGER,
    vues DOUBLE PRECISION,
    reactions DOUBLE PRECISION,
    commentaires INTEGER,
    date_insertion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE donnees_facebook_encoded (
    id SERIAL PRIMARY KEY,
    longueur_message DOUBLE PRECISION,
    nb_hashtags DOUBLE PRECISION,
    nb_tags DOUBLE PRECISION,
    contient_lien_externe DOUBLE PRECISION,
    contient_emoji DOUBLE PRECISION,
    contient_question DOUBLE PRECISION,
    type_post_photo DOUBLE PRECISION,
    type_post_album DOUBLE PRECISION,
    type_post_video DOUBLE PRECISION,
    type_post_lien DOUBLE PRECISION,
    type_post_evenement DOUBLE PRECISION,
    ratio_image DOUBLE PRECISION,
    nb_photos_album DOUBLE PRECISION,
    duree_video DOUBLE PRECISION,
    longueur_titre_video DOUBLE PRECISION,
    jour_lundi DOUBLE PRECISION,
    jour_mardi DOUBLE PRECISION,
    jour_mercredi DOUBLE PRECISION,
    jour_jeudi DOUBLE PRECISION,
    jour_vendredi DOUBLE PRECISION,
    jour_samedi DOUBLE PRECISION,
    heure_publication DOUBLE PRECISION,
    jours_depuis_dernier_post DOUBLE PRECISION,
    nb_posts_7j DOUBLE PRECISION,
    nb_posts_30j DOUBLE PRECISION,
    vues DOUBLE PRECISION,
    reactions DOUBLE PRECISION,
    commentaires DOUBLE PRECISION,
    reactions_classe TEXT,
    commentaires_classe TEXT,
    vues_classe TEXT,
    date_insertion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE modeles_classification_facebook (
    id SERIAL PRIMARY KEY,
    nom_modele TEXT NOT NULL,
    variable_cible TEXT NOT NULL,
    accuracy DOUBLE PRECISION,
    date_entrainement TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modele_binaire BYTEA NOT NULL
);