package gtok;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Trending {

    private static final String API_URL =
            "https://api.scrapecreators.com/v1/tiktok/get-trending-feed";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        String apiKey = System.getenv("SCRAPE_CREATORS_API_KEY");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println(
                    "Environment variable SCRAPE_CREATORS_API_KEY is missing."
            );
            return;
        }

        String json = getTrendingFeed(apiKey);

        List<TrendRow> rows = convertJsonToRows(json);

        // Highest view count first.
        rows.sort(
                Comparator.comparingLong(TrendRow::getViews).reversed()
        );

        printTable(rows);

        /*
         * When your SQL connection is ready:
         *
         * try (Connection connection = DriverManager.getConnection(
         *         "jdbc:sqlserver://SERVER;"
         *       + "databaseName=DATABASE;"
         *       + "encrypt=true;"
         *       + "trustServerCertificate=true;",
         *         "USERNAME",
         *         "PASSWORD")) {
         *
         *     insertRows(connection, rows);
         * }
         */
    }

    /**
     * Calls ScrapeCreators and returns the raw JSON response.
     */
    public static String getTrendingFeed(String apiKey) throws Exception {

        HttpClient client = HttpClient.newBuilder().build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new RuntimeException(
                    "API request failed. HTTP "
                    + response.statusCode()
                    + ": "
                    + response.body()
            );
        }

        return response.body();
    }

    /**
     * Converts ScrapeCreators JSON into table-ready Java rows.
     */
    public static List<TrendRow> convertJsonToRows(String json)
            throws Exception {

        JsonNode root = MAPPER.readTree(json);

        if (!root.path("success").asBoolean(false)) {
            throw new RuntimeException(
                    "ScrapeCreators returned success=false."
            );
        }

        System.out.println(
                "Credits charged: "
                + root.path("credits_charged").asInt(0)
        );

        System.out.println(
                "Credits remaining: "
                + root.path("credits_remaining").asInt(0)
        );

        JsonNode awemeList = root.path("aweme_list");

        List<TrendRow> rows = new ArrayList<>();

        if (!awemeList.isArray()) {
            return rows;
        }

        for (JsonNode item : awemeList) {

            TrendRow row = new TrendRow();

            row.setVideoId(
                    item.path("aweme_id").asText("")
            );

            row.setRegion(
                    item.path("region").asText("")
            );

            row.setDescription(
                    item.path("desc").asText("")
            );

            row.setCreateTimeUtc(
                    getCreateTime(item)
            );

            row.setUsername(
                    item.path("author")
                            .path("unique_id")
                            .asText("")
            );

            row.setNickname(
                    item.path("author")
                            .path("nickname")
                            .asText("")
            );

            JsonNode statistics = item.path("statistics");

            row.setViews(
                    statistics.path("play_count").asLong(0)
            );

            row.setLikes(
                    statistics.path("digg_count").asLong(0)
            );

            row.setComments(
                    statistics.path("comment_count").asLong(0)
            );

            row.setShares(
                    statistics.path("share_count").asLong(0)
            );

            row.setSaves(
                    statistics.path("collect_count").asLong(0)
            );

            row.setDownloads(
                    statistics.path("download_count").asLong(0)
            );

            row.setMusicTitle(
                    item.path("music")
                            .path("title")
                            .asText("")
            );

            row.setMusicAuthor(
                    item.path("music")
                            .path("author")
                            .asText("")
            );

            String videoUrl = item.path("url").asText("");

            if (videoUrl.isEmpty()) {
                videoUrl = item.path("share_info")
                        .path("share_url")
                        .asText("");
            }

            row.setVideoUrl(videoUrl);

            row.setAd(
                    item.path("is_ad").asBoolean(false)
            );

            row.setEngagementRate(
                    calculateEngagementRate(row)
            );

            rows.add(row);
        }

        return rows;
    }

    private static Instant getCreateTime(JsonNode item) {

        String createTimeUtc =
                item.path("create_time_utc").asText("");

        if (!createTimeUtc.isEmpty()) {
            try {
                return Instant.parse(createTimeUtc);
            } catch (Exception ignored) {
                // Use numeric timestamp below.
            }
        }

        long createTime =
                item.path("create_time").asLong(0);

        if (createTime > 0) {
            return Instant.ofEpochSecond(createTime);
        }

        return null;
    }

    /**
     * Engagement = likes + comments + shares + saves,
     * divided by views.
     */
    private static BigDecimal calculateEngagementRate(
            TrendRow row
    ) {

        if (row.getViews() <= 0) {
            return BigDecimal.ZERO;
        }

        long engagements =
                row.getLikes()
                + row.getComments()
                + row.getShares()
                + row.getSaves();

        return BigDecimal.valueOf(engagements)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(row.getViews()),
                        4,
                        RoundingMode.HALF_UP
                );
    }

    /**
     * Displays a concise table in the NetBeans output window.
     */
    public static void printTable(List<TrendRow> rows) {

        String format =
                "%-4s %-20s %-22s %12s %12s %10s "
                + "%10s %10s %10s %-20s%n";

        System.out.printf(
                format,
                "#",
                "Video ID",
                "Username",
                "Views",
                "Likes",
                "Comments",
                "Shares",
                "Saves",
                "Rate %",
                "Created UTC"
        );

        System.out.println(
                "------------------------------------------------"
                + "------------------------------------------------"
                + "----------------------------------------------"
        );

        int rank = 1;

        for (TrendRow row : rows) {
            System.out.printf(
                    format,
                    rank++,
                    truncate(row.getVideoId(), 20),
                    truncate("@" + row.getUsername(), 22),
                    formatNumber(row.getViews()),
                    formatNumber(row.getLikes()),
                    formatNumber(row.getComments()),
                    formatNumber(row.getShares()),
                    formatNumber(row.getSaves()),
                    row.getEngagementRate(),
                    row.getCreateTimeUtc() == null
                            ? ""
                            : row.getCreateTimeUtc().toString()
            );

            System.out.println(
                    "     Description: "
                    + truncate(
                            cleanText(row.getDescription()),
                            150
                    )
            );

            System.out.println(
                    "     URL: " + row.getVideoUrl()
            );

            System.out.println();
        }
    }

    /**
     * Inserts the extracted rows into SQL Server.
     *
     * Existing VideoID values are skipped.
     */
    public static int insertRows(
            Connection connection,
            List<TrendRow> rows
    ) throws Exception {

        String sql =
                "INSERT INTO dbo.TikTokTrending "
                + "(VideoID, RegionCode, VideoDescription, "
                + " CreateTimeUTC, Username, Nickname, "
                + " ViewCount, LikeCount, CommentCount, "
                + " ShareCount, SaveCount, DownloadCount, "
                + " EngagementRate, MusicTitle, MusicAuthor, "
                + " VideoURL, IsAd, RetrievedAtUTC) "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "       ?, ?, ?, ?, ?, ?, ?, GETUTCDATE() "
                + "WHERE NOT EXISTS "
                + "(SELECT 1 "
                + " FROM dbo.TikTokTrending "
                + " WHERE VideoID = ?)";

        int insertedRows = 0;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (TrendRow row : rows) {

                statement.setString(1, row.getVideoId());
                statement.setString(2, row.getRegion());
                statement.setString(3, row.getDescription());

                if (row.getCreateTimeUtc() == null) {
                    statement.setTimestamp(4, null);
                } else {
                    statement.setTimestamp(
                            4,
                            Timestamp.from(row.getCreateTimeUtc())
                    );
                }

                statement.setString(5, row.getUsername());
                statement.setString(6, row.getNickname());

                statement.setLong(7, row.getViews());
                statement.setLong(8, row.getLikes());
                statement.setLong(9, row.getComments());
                statement.setLong(10, row.getShares());
                statement.setLong(11, row.getSaves());
                statement.setLong(12, row.getDownloads());

                statement.setBigDecimal(
                        13,
                        row.getEngagementRate()
                );

                statement.setString(14, row.getMusicTitle());
                statement.setString(15, row.getMusicAuthor());
                statement.setString(16, row.getVideoUrl());
                statement.setBoolean(17, row.isAd());

                // Used by WHERE NOT EXISTS.
                statement.setString(18, row.getVideoId());

                statement.addBatch();
            }

            int[] results = statement.executeBatch();

            for (int result : results) {
                if (result > 0
                        || result == PreparedStatement.SUCCESS_NO_INFO) {
                    insertedRows++;
                }
            }
        }

        System.out.println(
                insertedRows + " TikTok rows inserted."
        );

        return insertedRows;
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .trim();
    }

    private static String truncate(
            String value,
            int maximumLength
    ) {

        if (value == null) {
            return "";
        }

        if (value.length() <= maximumLength) {
            return value;
        }

        return value.substring(
                0,
                maximumLength - 3
        ) + "...";
    }

    private static String formatNumber(long value) {
        return String.format("%,d", value);
    }

    /**
     * One clean relational-table row.
     */
    public static class TrendRow {

        private String videoId;
        private String region;
        private String description;
        private Instant createTimeUtc;

        private String username;
        private String nickname;

        private long views;
        private long likes;
        private long comments;
        private long shares;
        private long saves;
        private long downloads;

        private BigDecimal engagementRate;

        private String musicTitle;
        private String musicAuthor;
        private String videoUrl;

        private boolean ad;

        public String getVideoId() {
            return videoId;
        }

        public void setVideoId(String videoId) {
            this.videoId = videoId;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Instant getCreateTimeUtc() {
            return createTimeUtc;
        }

        public void setCreateTimeUtc(Instant createTimeUtc) {
            this.createTimeUtc = createTimeUtc;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public long getViews() {
            return views;
        }

        public void setViews(long views) {
            this.views = views;
        }

        public long getLikes() {
            return likes;
        }

        public void setLikes(long likes) {
            this.likes = likes;
        }

        public long getComments() {
            return comments;
        }

        public void setComments(long comments) {
            this.comments = comments;
        }

        public long getShares() {
            return shares;
        }

        public void setShares(long shares) {
            this.shares = shares;
        }

        public long getSaves() {
            return saves;
        }

        public void setSaves(long saves) {
            this.saves = saves;
        }

        public long getDownloads() {
            return downloads;
        }

        public void setDownloads(long downloads) {
            this.downloads = downloads;
        }

        public BigDecimal getEngagementRate() {
            return engagementRate;
        }

        public void setEngagementRate(
                BigDecimal engagementRate
        ) {
            this.engagementRate = engagementRate;
        }

        public String getMusicTitle() {
            return musicTitle;
        }

        public void setMusicTitle(String musicTitle) {
            this.musicTitle = musicTitle;
        }

        public String getMusicAuthor() {
            return musicAuthor;
        }

        public void setMusicAuthor(String musicAuthor) {
            this.musicAuthor = musicAuthor;
        }

        public String getVideoUrl() {
            return videoUrl;
        }

        public void setVideoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
        }

        public boolean isAd() {
            return ad;
        }

        public void setAd(boolean ad) {
            this.ad = ad;
        }
    }
}