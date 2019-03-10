package com.stackrating;

import com.stackrating.model.*;
import com.stackrating.storage.Storage;
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
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static com.stackrating.SortingPolicy.BY_RATING;
import static com.stackrating.SortingPolicy.BY_REPUTATION;
import static com.stackrating.Util.clamp;
import static com.stackrating.Util.parseInt;
import static java.util.Comparator.comparing;
import static spark.Spark.*;

public class Main {

    private static final boolean DEV_MODE = false;
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    final static int USERS_PER_PAGE = 50;
    final static int ENTRIES_PER_PAGE = 50;

    static private Storage storage;
    static private ContentUpdater contentUpdater;
    static private PlayerListCache playerListCache;

    public static void main(String[] args) throws Exception {

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
        playerListCache = new PlayerListCache(storage);
        contentUpdater = new ContentUpdater(storage);

        // If we did an unclean shutdown (left the database in a state where for instance positions
        // don't reflect a sorted rating/reputation or a question was downloaded but not yet
        // judged) we here give the user a chance to do a database fixup before launch.
        boolean safeStart = false; // TODO: Make cmd line argument
        if (safeStart) {
            contentUpdater.doDatabaseFixup(0);
        }

        reloadPlayerListCache();
        startSpark();

        // Save quota! Don't run in dev mode.
        if (!DEV_MODE) {
            contentUpdater.startLooping(Main::reloadPlayerListCache);
        }
    }

    private static void reloadPlayerListCache() {
        logger.info("Refreshing player list cache...");
        long start = System.currentTimeMillis();
        playerListCache.regenerateCache();
        long elapsedMs = System.currentTimeMillis() - start;
        logger.info("Player list cache loaded in " + (elapsedMs) / 1000 + " seconds.");
    }

    private static void startSpark() {

        staticFiles.location("/static");

        before("/*", (req, res) -> storage.openSession());
        after("/*", (req, res) -> storage.closeSession());

        get("/", (req, res) -> {
            res.redirect("/list/byRating");
            return null;
        });

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/rating/:userId", (req, res) -> {
            int userId = parseIntParam(req, ":userId");
            Player player = storage.findPlayer(userId).orElseThrow(NotFoundException::new);
            return String.format("%.2f", player.getRating());
        });

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/about", (req, res) -> {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("show", "about");
            return new ModelAndView(attrs, "about.ftl");
        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/getBadge", (req, res) ->
                new ModelAndView(new HashMap<String, String>(), "get-badge.ftl"),
                new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/stats", (req, res) -> {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("show", "stats");
            return new ModelAndView(attrs, "stats.ftl");
        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/list/:sort", (req, res) -> {
            SortingPolicy sortBy = req.params(":sort").equals("byRating") ? BY_RATING : BY_REPUTATION;
            int userCount = playerListCache.getUserCount();
            int numPages = (int) Math.ceil((double) userCount / USERS_PER_PAGE);
            int currentPage = parseInt(req.queryParams("page")).orElse(1);
            currentPage = clamp(1, currentPage, numPages);

            // Handle search query
            Optional<Player> highlightedPlayer = Optional.empty();
            String searchQuery = req.queryParams("userId");
            if (searchQuery != null) {
                searchQuery = searchQuery.trim();
                try {
                    int soughtUserId = Integer.parseInt(searchQuery);
                    highlightedPlayer = storage.findPlayer(soughtUserId);
                } catch (NumberFormatException e) {
                    // Do nothing
                }
            }

            // If search -> override current page
            if (highlightedPlayer.isPresent()) {
                Player player = highlightedPlayer.get();
                int pos = sortBy == BY_RATING ? player.getRatingPos() : player.getRepPos();
                currentPage = ((pos - 1) / USERS_PER_PAGE) + 1;
            }

            // Replaced by playerListCache
            //List<Player> usersOnPage = sortBy == BY_RATING
            //       ? storage.getByRatingPage(currentPage, USERS_PER_PAGE)
            //       : storage.getByRepPage(currentPage, USERS_PER_PAGE);
            List<Integer> userIdsOnPage = playerListCache.getIdsForPage(currentPage, USERS_PER_PAGE, sortBy);
            List<Player> usersOnPage = storage.getUsersByIds(userIdsOnPage);

            // Template attributes
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("show", "home");
            attrs.put("currentPage", currentPage);
            attrs.put("numPages", numPages);
            attrs.put("sortBy", sortBy == BY_RATING ? "rating" : "rep");
            attrs.put("useNumericId", searchQuery != null && !searchQuery.matches("\\d+"));
            attrs.put("userNotFound", searchQuery != null && searchQuery.matches("\\d+") && !highlightedPlayer.isPresent());
            attrs.put("highlightedUser", highlightedPlayer.map(Player::getId).orElse(-2));
            attrs.put("users", usersOnPage);

            // Return page
            return new ModelAndView(attrs, "list.ftl");

        }, new FreeMarkerEngine());

        ////////////////////////////////////////////////////////////////////////////////////////////
        get("/user/:userId", (req, res) -> {

            // Load content
            int userId = parseIntParam(req, ":userId");
            Player player = storage.findPlayer(userId).orElseThrow(NotFoundException::new);

            int numEntries = storage.getEntryCountForUser(userId);
            int numPages = (int) Math.ceil((double) numEntries / ENTRIES_PER_PAGE);
            int currentPage = parseInt(req.queryParams("page")).orElse(1);
            currentPage = clamp(1, currentPage, numPages);

            List<TimeDataPoint> ratingGraph = storage.getRatingGraph(userId);
            List<Entry> entriesOnThisPage = storage.getEntriesPage(userId, currentPage, ENTRIES_PER_PAGE);

            // Prep template attributes
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("currentPage", currentPage);
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
            int gameId = parseIntParam(req, ":gameId");
            Game game = storage.findGame(gameId).orElseThrow(NotFoundException::new);

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

            int userId = parseIntParam(req, ":userId");
            Player player = storage.findPlayer(userId).orElseThrow(NotFoundException::new);

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
        
        exception(BadRequestException.class, (e, req, res) -> res.status(400));
        exception(NotFoundException.class, (e, req, res) -> res.status(404));
    }

    private static int parseIntParam(Request req, String param) {
        return parseInt(req.params(param))
                .orElseThrow(BadRequestException::new);
    }

    private static void shutdown() throws InterruptedException {
        // Shutdown ContentUpdater first since a graceful shutdown may take a long while and we can
        // just as well continue to serve requests meanwhile.
        logger.info("Shutting down ContentUpdater...");
        contentUpdater.shutdown();

        logger.info("Shutting down Spark...");
        Spark.stop();
    }
}
