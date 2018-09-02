package com.stackrating;


import com.stackrating.model.*;
import com.stackrating.storage.Storage;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ModelAndView;
import spark.Request;
import spark.Spark;
import spark.template.freemarker.FreeMarkerEngine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.stackrating.Main.SortingPolicy.BY_RATING;
import static com.stackrating.Main.SortingPolicy.BY_REPUTATION;
import static java.util.Comparator.comparing;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.SparkBase.secure;
import static spark.SparkBase.staticFileLocation;


public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public enum SortingPolicy { BY_RATING, BY_REPUTATION }

    final static int USERS_PER_PAGE = 50;
    final static int ENTRIES_PER_PAGE = 50;

    static private Storage storage;
    static private ContentUpdater contentUpdater;

    // Player list page is too slow without a cache. Luckily it seems like this is the only data
    // that needs to be cached though. Right now (2016-01-01) it takes 16 seconds to load and
    // requires 1 GB extra memory.
    static private PlayerListCache playerListCache = new PlayerListCache();

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length < 2) {
            logger.error("Usage: java -jar stackrating.jar path/to/keystore.jks keystorepass");
            System.exit(1);
        }

        String keystorePath = args[0];
        String keystorePass = args[1];
        if (!Files.exists(Paths.get(keystorePath))) {
            logger.error("Keystore file not found: " + keystorePath);
            System.exit(1);
        }

        // For graceful shutdown on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                shutdown();
            } catch (Exception e) {
                logger.error("Failed graceful shutdown.", e);
                e.printStackTrace();
            }
        }));

        storage = new Storage();
        contentUpdater = new ContentUpdater(storage);

        // If we did an unclean shutdown (left the database in a state where for instance positions
        // don't reflect a sorted rating/reputation or a question was downloaded but not yet judged)
        // we here give the user a chance to do a database fixup before launch.
        boolean safeStart = false; // TODO: Make cmd line argument
        if (safeStart) {
            contentUpdater.doDatabaseFixup(0);
        }

        reloadPlayerListCache();
        startSpark(keystorePath, keystorePass);

        // Caution; Don't activate this unnecessarily during development (save the quota)
        try {
            contentUpdater.startLooping(Main::reloadPlayerListCache);
        } catch (SQLException ex) {
            logger.error("SQLException: " + ex.getMessage(), ex);
        }
    }

    private static void reloadPlayerListCache() {
        logger.info("(Re)loading player list cache into memory...");
        long start = System.currentTimeMillis();
        playerListCache.setEntries(storage.getAllPlayerIdsAndPositions());
        long elapsedMs = System.currentTimeMillis() - start;
        logger.info("Player list cache loaded in " + (elapsedMs) / 1000 + " seconds.");
    }

    private static void startSpark(String keystorePath, String keystorePass) {

        secure(keystorePath, keystorePass, null, null);

        staticFileLocation("/static");

        get("/", (req, res) -> {
            res.redirect("/list/byRating");
            return null;
        });

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/rating/:userId", (req, res) -> {
            int userId = parseIntOrThrow(req.params(":userId"), NotFoundException::new);
            Player player = storage.getPlayer(userId);
            if (player == null) {
                throw new NotFoundException();
            }
            return String.format("%.2f", storage.getPlayer(userId).getRating());
        });

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/about", (req, res) -> {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("show", "about");
            return new ModelAndView(attrs, "about.ftl");
        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/getBadge", (req, res) -> {
            return new ModelAndView(new HashMap<String, String>(), "get-badge.ftl");
        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/stats", (req, res) -> {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("show", "stats");
            return new ModelAndView(attrs, "stats.ftl");
        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/list/:sort", (req, res) -> {

            SortingPolicy sortingPolicy = req.params(":sort").equals("byRating") ? BY_RATING : BY_REPUTATION;
            int userCount = playerListCache.getUserCount();
            int numPages = (int) Math.ceil((double) userCount / USERS_PER_PAGE);
            int pageParam = getIntParam(req, "page", 1);
            pageParam = clamp(1, pageParam, numPages);

            // Handle search query
            Player highlightedPlayer = null;
            String searchQuery = req.queryParams("userId");
            if (searchQuery != null) {
                searchQuery = searchQuery.trim();
                if (!searchQuery.matches("\\d+")) {
                    // Do nothing
                } else {
                    int soughtUserId = Integer.parseInt(searchQuery);
                    highlightedPlayer = storage.getPlayer(soughtUserId);
                }
            }
            // Set current page
            int currentPage;
            if (highlightedPlayer != null) {
                int pos = sortingPolicy == BY_RATING ? highlightedPlayer.getRatingPos() : highlightedPlayer.getRepPos();
                currentPage = ((pos - 1) / USERS_PER_PAGE) + 1;
            } else {
                currentPage = pageParam;
            }

//            List<Player> usersOnPage = sortingPolicy == BY_RATING
//                    ? storage.getByRatingPage(currentPage, USERS_PER_PAGE)
//                    : storage.getByRepPage(currentPage, USERS_PER_PAGE);
            List<Integer> userIdsOnThisPage = playerListCache.getIdsForPage(currentPage, USERS_PER_PAGE, sortingPolicy);
            List<Player> usersOnThisPage = storage.getUsersByIds(userIdsOnThisPage);

            // Template attributes
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("show", "home");
            attrs.put("currentPage", currentPage);
            attrs.put("numPages", numPages);
            attrs.put("sortBy", sortingPolicy == BY_RATING ? "rating" : "rep");
            attrs.put("userNotFound", searchQuery != null && searchQuery.length() > 0 && highlightedPlayer == null);
            attrs.put("highlightedUser", highlightedPlayer == null ? -2 : highlightedPlayer.getId());
            attrs.put("users", usersOnThisPage);

            // Return page
            return new ModelAndView(attrs, "list.ftl");

        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/user/:userId", (req, res) -> {

            // Load content
            int userId = parseIntOrThrow(req.params(":userId"), NotFoundException::new);
            Player player = storage.getPlayer(userId);
            if (player == null)
                throw new NotFoundException();

            int numEntries = storage.getEntryCountForUser(userId);
            int numPages = (int) Math.ceil((double) numEntries / ENTRIES_PER_PAGE);
            int currentPageNum = getIntParam(req, "page", 1);
            currentPageNum = clamp(1, currentPageNum, numPages);

            List<TimeDataPoint> ratingGraph = storage.getRatingGraph(userId);
            List<Entry> entriesOnThisPage = storage.getEntriesPage(userId, currentPageNum, ENTRIES_PER_PAGE);

            // Prep template attributes
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("currentPage", currentPageNum);
            attrs.put("player", player);
            attrs.put("numPages", numPages);
            attrs.put("entries", entriesOnThisPage);
            attrs.put("ratingGraph", ratingGraph);
            
            // Return page
            return new ModelAndView(attrs, "player.ftl");
        }, new FreeMarkerEngine());


        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/question/:gameId", (req, res) -> {

            // Load content
            int gameId = parseIntOrThrow(req.params(":gameId"), NotFoundException::new);
            Game game = storage.getGame(gameId);
            if (game == null)
                throw new NotFoundException();

            List<Entry> entries = storage.getEntriesForGame(gameId);
            entries.sort(comparing(Entry::getVotes).reversed());

            // Prep template attributes
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("game", game);
            attrs.put("entries", entries);

            // Return page
            return new ModelAndView(attrs, "game.ftl");

        }, new FreeMarkerEngine());

        get("/badge/:userId", (req, res) -> {

            int userId = parseIntOrThrow(req.params(":userId"), NotFoundException::new);
            Player player = storage.getPlayer(userId);
            if (player == null)
                throw new NotFoundException();

            Image template = ImageIO.read(Main.class.getResourceAsStream("/badge.png"));

            BufferedImage bi = new BufferedImage(template.getWidth(null), template.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) bi.getGraphics();

//            g.setColor(Color.WHITE);
//            g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
            g.drawImage(template, 0, 0, null);

            g.setFont(g.getFont().deriveFont(15f));
            g.setColor(new Color(0, 119, 204));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

//          g.setRenderingHint(
//                  RenderingHints.KEY_TEXT_ANTIALIASING,
//                  RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);

            String str = String.format("%.2f", player.getRating());
            Rectangle2D r = g.getFontMetrics().getStringBounds(str, g);
            g.drawString(str, (int) (bi.getWidth() - 3 - r.getWidth()), 20);
            try {
                ImageIO.write(bi, "PNG", res.raw().getOutputStream());
            } catch (IOException e) {
                logger.warn("Could not write badge PNG response: " + e.getMessage());
            }
            return null;
        });
        
        exception(NotFoundException.class, (e, request, response) -> {
            response.status(404);
            response.body("Resource not found");
        });
    }
    
    private static void shutdown() throws InterruptedException {
        // Shutdown ContentUpdater first since a graceful shutdown may take a long while and we can
        // just as well continue to serve requests meanwhile.
        logger.info("Shutting down ContentUpdater...");
        contentUpdater.shutdown();

        logger.info("Shutting down Spark...");
        Spark.stop();
    }


    public static String getParam(Request req, String key, String def) {
        String val = req.queryMap(key).value();
        return val != null ? val : def;
    }

//    public static int getIntParamInRange(Request req, String key, int min, int max) {
//        int n = getIntParamInRange(req, key, min);
//        return Math.max(min, Math.min(n, max));
//    }

    public static int clamp(int min, int val, int max) {
        return Math.max(min, Math.min(val, max));
    }

    public static int getIntParam(Request req, String key, int def) {
        String val = getParam(req, key, null);
        try {
            if (val != null)
                return Integer.parseInt(val);
        } catch (NumberFormatException nfe) {
        }
        return def;
    }

    @SafeVarargs
    public static <T extends Comparable<T>> T max(T... ts) {
        return Collections.max(Arrays.asList(ts));
    }

    /*
    public static <T> List<T> getPage(List<T> list, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, list.size());
        return list.subList(from, to);
    }
    
    public static <T> List<T> getPageReversed(List<T> list, int page, int pageSize) {
        int from = (page - 1) * pageSize;
        int to = Math.min(from + pageSize, list.size());
        List<T> pageContent = new ArrayList<>(pageSize);
        for (int i = from; i < to; i++)
            pageContent.add(list.get(list.size() - i - 1));
        return pageContent;
    }


    public static int numPages(int items, int pageSize) {
        return (int) Math.ceil((double) items / pageSize);
    }
*/

    public static <T extends Exception> int parseIntOrThrow(String str, Supplier<T> exSup) throws T {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            throw exSup.get();
        }
    }

    // index of the search key, if it is contained in the array; otherwise, (-(insertion point) - 1)
    public static <T, K extends Comparable<K>> int findIndex(List<T> sortedList, K key, Function<T, K> f) {
        int low = 0;
        int high = sortedList.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            T t = sortedList.get(mid);
            int cmp = f.apply(t).compareTo(key);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }

    public static <T, K extends Comparable<K>> T find(List<T> sortedList, K key, Function<T, K> f) {
        int i = findIndex(sortedList, key, f);
        return i < 0 ? null : sortedList.get(i);
    }
    
    public static String formatInstant(Instant i) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(ZoneOffset.UTC)
                                .format(i);
    }
}
