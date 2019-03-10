package com.stackrating.storage;

import com.stackrating.SortingPolicy;
import com.stackrating.db.EntryMapper;
import com.stackrating.db.GameMapper;
import com.stackrating.db.PlayerMapper;
import com.stackrating.log.Progress;
import com.stackrating.model.Entry;
import com.stackrating.model.Game;
import com.stackrating.model.Player;
import com.stackrating.model.TimeDataPoint;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingDouble;

public class Storage {

    private final static Logger logger = LoggerFactory.getLogger(Storage.class);

    private final SqlSessionFactory sessionFactory;

    ThreadLocal<SqlSession> session = new ThreadLocal<>();

    public Storage() throws IOException {
        InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
        Properties dbProperties = new Properties();
        dbProperties.load(new FileReader("db.properties"));
        sessionFactory = new SqlSessionFactoryBuilder().build(inputStream, dbProperties);
    }

    public Closeable openSession() {
        session.set(sessionFactory.openSession(ExecutorType.BATCH, true));
        return this::closeSession;
    }

    public void closeSession() {
        session.get().close();
        session.remove();
    }

    // Convenience method
    public <T> T getMapper(Class<T> mapperClass) {
        return session.get().getMapper(mapperClass);
    }

    public int getUserCount() {
        return getMapper(PlayerMapper.class)
                .getNumPlayers();
    }

    public int getEntryCountForUser(int userId) {
        return getMapper(EntryMapper.class)
                .getEntryCountForUser(userId);
    }

    public List<Entry> getEntriesForGame(int gameId) {
        return getMapper(EntryMapper.class)
                .getEntriesForGame(gameId);
    }

//    public void storeUser(Player p) {
//        try (SqlSession session = sessionFactory.openSession()) {
//            session.getMapper(PlayerMapper.class)
//                    .storeUser(p);
//        }
//    }

//    public void storeGame(Game game) {
//        try (SqlSession session = sessionFactory.openSession()) {
//            session.getMapper(GameMapper.class)
//                    .storeGame(game);
//        }
//    }

//    public void storeEntry(PlayerListEntry entry) {
//        try (SqlSession session = sessionFactory.openSession()) {
//            session.getMapper(EntryMapper.class)
//                   .storeEntry(entry);
//        }
//    }

    public Optional<Game> findGame(int id) {
        return Optional.ofNullable(getMapper(GameMapper.class).getGame(id));
    }

    public Optional<Player> findPlayer(int id) {
        return Optional.ofNullable(getMapper(PlayerMapper.class).getPlayer(id));
    }

    public List<TimeDataPoint> getRatingGraph(int userId) {
        EntryMapper entryMapper = getMapper(EntryMapper.class);
            
        TreeMap<Long, Double> deltaPerDay =
                entryMapper.getRatingDeltas(userId)
                        .stream()
                        .filter(tdp -> tdp.getVal() != 0)
                        .collect(groupingBy(Storage::truncateTimestamp,
                                TreeMap::new,
                                summingDouble(TimeDataPoint::getVal)));

        List<TimeDataPoint> absoluteRatings = new ArrayList<>();
        double rating = 1500;
        for (Map.Entry<Long, Double> entry : deltaPerDay.entrySet()) {
            rating += entry.getValue();
            absoluteRatings.add(new TimeDataPoint(entry.getKey() / 1000, rating));
        }

        return absoluteRatings;
    }

    public List<Entry> getEntriesPage(int userId, int page, int pageSize) {
        return getMapper(EntryMapper.class)
                .getEntriesPage(userId, page, pageSize);
    }

    private static long truncateTimestamp(TimeDataPoint rd) {
        return rd.getTimestamp() - rd.getTimestamp() % ChronoUnit.DAYS.getDuration().toMillis();
    }
    
//    public void setVotes(int entryId, int newVotes) {
//        try (SqlSession session = sessionFactory.openSession()) {
//            session.getMapper(EntryMapper.class)
//                   .setVotes(entryId, newVotes);
//        }
//    }

    public void rejudgeGames(int fromGameId) {
        new RatingUpdater(session.get()).recalcRatings(fromGameId);
    }

    /** Create or update existing game. */
    public void upsertGame(Game game) {
        if (game.getTitle().length() > 100) {
            game.setTitle(game.getTitle().substring(0, 100));
        }
        GameMapper gameMapper = getMapper(GameMapper.class);
        Game existing = gameMapper.getGame(game.getId());
        if (existing == null) {
            gameMapper.insertGame(game);
        } else {
            gameMapper.updateGame(game);
        }
    }

    /** Create or update existing player. */
    public void upsertUser(Player player) {
        if (player.getDisplayName().length() > 40) {
            player.setDisplayName(player.getDisplayName().substring(0, 40));
        }
        PlayerMapper playerMapper = getMapper(PlayerMapper.class);
        Player existing = playerMapper.getPlayer(player.getId());
        if (existing == null) {
            playerMapper.insertPlayer(player);
        } else {
            playerMapper.updatePlayer(player);
        }
    }

    /** Create or update existing entry. */
    public void upsertEntry(Entry entry) {
        EntryMapper entryMapper = getMapper(EntryMapper.class);
        Entry existing = entryMapper.getEntry(entry.getId(), entry.getGameId());
        if (existing == null) {
            entryMapper.insertEntry(entry);
        } else {
            entryMapper.updateEntry(entry);
        }
    }

    public void updateEntry(Entry entry) {
        getMapper(EntryMapper.class).updateEntry(entry);
    }

    public Entry getEntry(int id, int gameId) {
        return getMapper(EntryMapper.class).getEntry(id, gameId);
    }

//    public List<Player> getAllPlayers() {
//        try (SqlSession session = sessionFactory.openSession()) {
//            return session.getMapper(PlayerMapper.class).getAllPlayers();
//        }
//    }

    public Cursor<Integer> getAllPlayerIds(SortingPolicy sortingPolicy) {
        String orderBy = sortingPolicy == SortingPolicy.BY_RATING ? "rating" : "rep";
        return getMapper(PlayerMapper.class).getAllPlayerIds(orderBy);
    }

    public List<Player> getUsersByIds(List<Integer> ids) {
        List<Player> players = getMapper(PlayerMapper.class).getPlayers(ids);
        // Make sure they are sorted according to ids parameter.
        players.sort(comparing(player -> ids.indexOf(player.getId())));
        return players;
    }
    
    public void updateRepPositions() {
        PlayerMapper playerMapper = getMapper(PlayerMapper.class);
        int batchSize = 50000;
        int maxPlayerId = playerMapper.getMaxPlayerId();
        Progress progress = new Progress(logger, "Updating rep positions...", maxPlayerId);
        int id = 0;
        while (id <= maxPlayerId) {
            playerMapper.updateRepPositions(id, id + batchSize);
            progress.incProgress(batchSize);
            id += batchSize; // +1 since SQL BETWEEN condition is inclusive.
            try {
                session.get().flushStatements();
            } catch (Exception e) {
                logger.error("Error flushing statements.", e);
            }
        }
    }

    public int getCycleStartGameId() {
        return getMapper(GameMapper.class).getCycleStartGameId();
    }

    public void batchUpdateLastVisit(Instant from, Instant to, Instant visitTime) {
        getMapper(GameMapper.class).batchUpdateLastVisit(
                Timestamp.from(from),
                Timestamp.from(to),
                Timestamp.from(visitTime));
    }
}
