import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FacebookCollector {

    static final String TOKEN = "EAAUhFWDqnIYBSiNUyGb5hvevoInIcsjuuZBPdvX2ttsfRjeQiTYGsuTCDKDlZBPLkWpItfDixtyaFmWjVAlA5oaKW0ZAWmlWy0sGarHRKR015lq5k7UulZCDpXLEPlRGsOiNpejEM17cZAlGMn5wyz6nlvksyzJW2JHG04GxZB6nZC0aZCIiI6A562Q8ceutsSRU0ObEaRqluTL1GYdJviMR8R5EbRUF2dN4w5xPGuc58yUH";
    static final String PAGE_ID = "464664200069364";
    static final String SINCE = "2024-08-01";
    static final String UNTIL = "2026-07-31";
    static final String GRAPH_URL = "https://graph.facebook.com/v21.0";

    static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    static class Json {
        private final String s;
        private int i;
        private Json(String s) { this.s = s; }

        static Object parse(String s) {
            Json p = new Json(s);
            p.skipWs();
            return p.parseValue();
        }

        private void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object parseValue() {
            skipWs();
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++; skipWs();
            if (s.charAt(i) == '}') { i++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs(); i++;
                map.put(key, parseValue());
                skipWs();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                i++; break;
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; skipWs();
            if (s.charAt(i) == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                i++; break;
            }
            return list;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (s.charAt(i) != '"') {
                char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                    char e = s.charAt(i);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
                i++;
            }
            i++;
            return sb.toString();
        }

        private Object parseNumber() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) { return o == null ? new LinkedHashMap<>() : (Map<String, Object>) o; }
    @SuppressWarnings("unchecked")
    static List<Object> arr(Object o) { return o == null ? new ArrayList<>() : (List<Object>) o; }
    static String text(Object o, String def) { return o == null ? def : o.toString(); }
    static int intOf(Object o, int def) { return o instanceof Double ? (int) (double) (Double) o : def; }
    static Double doubleOf(Object o) { return o instanceof Double ? (Double) o : null; }
    static boolean boolOf(Object o) { return Boolean.TRUE.equals(o); }

    static Map<String, String> params(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int k = 0; k < keyValuePairs.length; k += 2) {
            map.put(keyValuePairs[k], keyValuePairs[k + 1]);
        }
        map.put("access_token", TOKEN);
        return map;
    }

    static Map<String, Object> fetchJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return obj(Json.parse(response.body()));
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("message", e.getMessage());
            error.put("error", msg);
            return error;
        }
    }

    static String buildUrl(String base, Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (query.length() > 0) query.append("&");
            query.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                 .append("=")
                 .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return query.length() > 0 ? base + "?" + query : base;
    }

    static Map<String, Object> callApi(String url, Map<String, String> params) {
        return fetchJson(buildUrl(url, params));
    }

    static void printError(String context, Map<String, Object> resp) {
        System.out.println("ERREUR API " + context + " :");
        System.out.println(resp.get("error"));
    }

    static List<Map<String, Object>> getPosts() {
        List<Map<String, Object>> posts = new ArrayList<>();

        Map<String, String> params = params(
                "fields",
                "id,created_time,message,message_tags,"
              + "attachments{media_type,url,title,media{image{height,width}},"
              + "target,subattachments{media_type,target}}",
                "since", SINCE,
                "until", UNTIL,
                "limit", "50"
        );

        String nextUrl = buildUrl(GRAPH_URL + "/" + PAGE_ID + "/posts", params);

        while (nextUrl != null) {
            Map<String, Object> resp = fetchJson(nextUrl);

            if (resp.containsKey("error")) {
                printError("lors de la récupération des posts", resp);
                break;
            }

            for (Object item : arr(resp.get("data"))) posts.add(obj(item));

            Map<String, Object> paging = obj(resp.get("paging"));
            nextUrl = paging.containsKey("next") ? text(paging.get("next"), null) : null;
        }
        return posts;
    }

    static Map<String, Object> getVideoInfo(String videoId) {
        if (videoId == null) return new LinkedHashMap<>();
        Map<String, String> params = params("fields", "length,title,live_status");
        return callApi(GRAPH_URL + "/" + videoId, params);
    }

    static double[] getInsights(String postId) {
        Map<String, String> params = params("metric", "post_media_view,post_reactions_by_type_total");
        Map<String, Object> resp = callApi(GRAPH_URL + "/" + postId + "/insights", params);

        double vues = 0;
        double reactions = 0;

        for (Object m : arr(resp.get("data"))) {
            Map<String, Object> metric = obj(m);
            String name = text(metric.get("name"), "");
            List<Object> values = arr(metric.get("values"));
            if (values.isEmpty()) continue;
            Object value = obj(values.get(0)).get("value");

            if (name.equals("post_media_view") && value instanceof Double) {
                vues = (Double) value;
            } else if (name.equals("post_reactions_by_type_total") && value instanceof Map) {
                for (Object v : obj(value).values()) {
                    if (v instanceof Double) reactions += (Double) v;
                }
            }
        }
        return new double[]{vues, reactions};
    }

    static int getCommentsCount(String postId) {
        Map<String, String> params = params("summary", "true");
        Map<String, Object> resp = callApi(GRAPH_URL + "/" + postId + "/comments", params);
        return intOf(obj(resp.get("summary")).get("total_count"), 0);
    }

    static class ContentInfo {
        String typePost;
        String ratioImage;
        Integer nbPhotosAlbum;
        Double dureeVideo;
        Boolean videoEnDirect;
        Integer longueurTitreVideo;
    }

    static ContentInfo analyzeContent(Map<String, Object> post) {
        ContentInfo info = new ContentInfo();
        List<Object> data = arr(obj(post.get("attachments")).get("data"));

        if (data.isEmpty()) {
            info.typePost = "texte";
            return info;
        }

        Map<String, Object> first = obj(data.get(0));
        String mediaType = text(first.get("media_type"), "");

        switch (mediaType) {
            case "album":
                fillAlbumInfo(first, info);
                break;
            case "video":
                info.typePost = "video";
                fillVideoInfo(first, info);
                break;
            case "photo":
                info.typePost = "photo";
                fillPhotoRatio(first, info);
                break;
            case "link":
                info.typePost = "lien";
                break;
            case "event":
                info.typePost = "evenement";
                break;
            default:
                info.typePost = mediaType.isEmpty() ? "texte" : mediaType;
        }

        return info;
    }

    static void fillAlbumInfo(Map<String, Object> first, ContentInfo info) {
        info.typePost = "album";
        int nbPhotos = 0;
        for (Object it : arr(obj(first.get("subattachments")).get("data"))) {
            if (text(obj(it).get("media_type"), "").equals("photo")) nbPhotos++;
        }
        info.nbPhotosAlbum = nbPhotos;
    }

    static void fillVideoInfo(Map<String, Object> attachment, ContentInfo info) {
        String videoId = text(obj(attachment.get("target")).get("id"), null);
        Map<String, Object> videoData = getVideoInfo(videoId);

        info.dureeVideo = doubleOf(videoData.get("length"));

        String liveStatus = text(videoData.get("live_status"), "");

        info.videoEnDirect = liveStatus.equalsIgnoreCase("LIVE");

        String title = text(videoData.get("title"), "");
        info.longueurTitreVideo = title.length();
    }

    static void fillPhotoRatio(Map<String, Object> attachment, ContentInfo info) {
        Map<String, Object> image = obj(obj(attachment.get("media")).get("image"));
        Double width = doubleOf(image.get("width"));
        Double height = doubleOf(image.get("height"));
        if (width != null && height != null && height != 0) {
            info.ratioImage = simplifierRatio(width.intValue(), height.intValue());
        }
    }

    static String simplifierRatio(int largeur, int hauteur) {
        int diviseur = pgcd(largeur, hauteur);
        return (largeur / diviseur) + ":" + (hauteur / diviseur);
    }

    static int pgcd(int a, int b) {
        return b == 0 ? a : pgcd(b, a % b);
    }

    static boolean containsExternalLink(Map<String, Object> post) {
        List<Object> data = arr(obj(post.get("attachments")).get("data"));
        for (Object it : data) {
            Map<String, Object> att = obj(it);
            if (text(att.get("media_type"), "").equals("link") && att.get("url") != null) {
                return true;
            }
        }
        return false;
    }

    static final Pattern HASHTAG_PATTERN = Pattern.compile("#\\w+");
    static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}]");

    static int countHashtags(String message) {
        if (message == null) return 0;
        Matcher m = HASHTAG_PATTERN.matcher(message);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    static int countEmojis(String message) {
        if (message == null) return 0;
        Matcher m = EMOJI_PATTERN.matcher(message);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    static boolean containsQuestion(String message) {
        return message != null && message.contains("?");
    }

    static int countTags(Map<String, Object> post) {
        Object rawTags = post.get("message_tags");
        int total = 0;
        if (rawTags instanceof Map) {
            for (Object value : ((Map<?, ?>) rawTags).values()) {
                if (value instanceof List) total += ((List<?>) value).size();
            }
        } else if (rawTags instanceof List) {
            total = ((List<?>) rawTags).size();
        }
        return total;
    }

    static Map<String, String> buildRow(Map<String, Object> post,
                                         Integer joursDepuisDernierPost,
                                         int nbPosts7j,
                                         int nbPosts30j) {
        String postId = text(post.get("id"), "");
        OffsetDateTime dt = OffsetDateTime.parse(text(post.get("created_time"), ""),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
        String dateStr = dt.toLocalDate().toString();
        String message = text(post.get("message"), "");

        ContentInfo content = analyzeContent(post);

        double[] insightsResult = getInsights(postId);
        double vues = insightsResult[0];
        double reactions = insightsResult[1];
        int commentaires = getCommentsCount(postId);

        String vuesFinales = (content.typePost.equals("video") || content.typePost.equals("album"))
                ? String.valueOf((long) vues) : "";

        Map<String, String> row = new LinkedHashMap<>();
        row.put("longueur_message", String.valueOf(message.length()));
        row.put("nb_hashtags", String.valueOf(countHashtags(message)));
        row.put("nb_tags", String.valueOf(countTags(post)));
        row.put("contient_lien_externe", String.valueOf(containsExternalLink(post)));
        row.put("contient_emoji", String.valueOf(countEmojis(message) > 0));
        row.put("contient_question", String.valueOf(containsQuestion(message)));
        row.put("type_post", content.typePost);
        row.put("ratio_image", content.ratioImage != null ? content.ratioImage : "");
        row.put("nb_photos_album", content.nbPhotosAlbum != null ? String.valueOf(content.nbPhotosAlbum) : "");
        row.put("duree_video", content.dureeVideo != null ? String.valueOf(content.dureeVideo) : "");
        row.put("longueur_titre_video", content.longueurTitreVideo != null ? String.valueOf(content.longueurTitreVideo) : "");
        row.put("jour_semaine", dt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH));
        row.put("heure_publication", String.valueOf(dt.getHour()));
        row.put("jours_depuis_dernier_post", joursDepuisDernierPost != null ? String.valueOf(joursDepuisDernierPost) : "");
        row.put("nb_posts_7j", String.valueOf(nbPosts7j));
        row.put("nb_posts_30j", String.valueOf(nbPosts30j));
        row.put("vues", vuesFinales);
        row.put("reactions", String.valueOf((long) reactions));
        row.put("commentaires", String.valueOf(commentaires));

        return row;
    }

    static List<Map<String, String>> buildRows(List<Map<String, Object>> posts) {
        List<Map<String, Object>> sorted = new ArrayList<>(posts);
        sorted.sort(Comparator.comparing(p -> text(p.get("created_time"), "")));

        List<LocalDate> dates = new ArrayList<>();
        for (Map<String, Object> p : sorted) {
            OffsetDateTime dt = OffsetDateTime.parse(text(p.get("created_time"), ""),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));
            dates.add(dt.toLocalDate());
        }

        List<Map<String, String>> rows = new ArrayList<>();
        int total = sorted.size();

        try {
            for (int i = 0; i < sorted.size(); i++) {
                System.out.println("[" + (i + 1) + "/" + total + "] Traitement du post "
                        + text(sorted.get(i).get("id"), "") + "...");

                LocalDate current = dates.get(i);

                Integer joursDepuisDernierPost = (i == 0) ? null
                        : (int) ChronoUnit.DAYS.between(dates.get(i - 1), current);

                int nbPosts7j = 0;
                int nbPosts30j = 0;
                for (int j = 0; j < i; j++) {
                    long diff = ChronoUnit.DAYS.between(dates.get(j), current);
                    if (diff <= 7) nbPosts7j++;
                    if (diff <= 30) nbPosts30j++;
                }

                rows.add(buildRow(sorted.get(i), joursDepuisDernierPost, nbPosts7j, nbPosts30j));
            }
        } catch (Exception e) {
            System.out.println("Erreur en cours de route, sauvegarde de " + rows.size() + " publications déjà traitées...");
        }

        return rows;
    }

    static void writeCsv(List<Map<String, String>> rows, String filename) {
        if (rows.isEmpty()) {
            System.out.println("Aucune ligne à sauvegarder (0 publication récupérée). "
                    + "Vérifie les messages d'erreur ci-dessus (token expiré ou permission manquante).");
            return;
        }

        try (FileWriter writer = new FileWriter(filename, StandardCharsets.UTF_8)) {
            List<String> headers = new ArrayList<>(rows.get(0).keySet());
            writer.write(String.join(",", headers) + "\n");
            for (Map<String, String> row : rows) {
                writer.write(toCsvLine(row, headers) + "\n");
            }
            System.out.println("Données sauvegardées dans " + filename);
        } catch (IOException e) {
            System.out.println("Erreur lors de l'écriture du CSV : " + e.getMessage());
        }
    }

    static String toCsvLine(Map<String, String> row, List<String> headers) {
        List<String> values = new ArrayList<>();
        for (String h : headers) {
            String v = row.getOrDefault(h, "").replace("\"", "\"\"");
            values.add("\"" + v + "\"");
        }
        return String.join(",", values);
    }

    public static void main(String[] args) {
        List<Map<String, Object>> posts = getPosts();
        System.out.println(posts.size() + " publications trouvées entre " + SINCE + " et " + UNTIL);

        List<Map<String, String>> rows = buildRows(posts);
        writeCsv(rows, "output/features_facebook.csv");
    }
}