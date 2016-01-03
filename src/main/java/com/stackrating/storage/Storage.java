package com.stackrating.storage;

import com.stackrating.PlayerListCache;
import com.stackrating.db.EntryMapper;
import com.stackrating.db.GameMapper;
import com.stackrating.db.PlayerMapper;
import com.stackrating.log.Progress;
import com.stackrating.model.Entry;
import com.stackrating.model.Game;
import com.stackrating.model.Player;
import com.stackrating.model.TimeDataPoint;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingDouble;

public class Storage {

    private final static Logger logger = LoggerFactory.getLogger(Storage.class);

    SqlSessionFactory sessionFactory;

    public Storage() throws IOException {
        InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");
        Properties dbProperties = new Properties();
        dbProperties.load(Storage.class.getResourceAsStream("/db.properties"));
        sessionFactory = new SqlSessionFactoryBuilder().build(inputStream, dbProperties);
    }

    public int getUserCount() {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(PlayerMapper.class)
                          .getNumPlayers();
        }
    }

    public int getEntryCountForUser(int userId) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(EntryMapper.class)
                          .getEntryCountForUser(userId);
        }
    }

    public List<Entry> getEntriesForGame(int gameId) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(EntryMapper.class)
                          .getEntriesForGame(gameId);
        }
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

    public Game getGame(int id) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(GameMapper.class)
                          .getGame(id);
        }
    }

    public Player getPlayer(int id) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(PlayerMapper.class)
                          .getPlayer(id);
        }
    }

    /* Since the introduction of PlayerListCache the two methods below are no longer used.
    public List<Player> getByRatingPage(int page, int pageSize) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(PlayerMapper.class)
                          .getUsersPage("rating", page, pageSize);
        }
    }

    public List<Player> getByRepPage(int page, int pageSize) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(PlayerMapper.class)
                    .getUsersPage("rep", page, pageSize);
        }
    }
    */

    public List<TimeDataPoint> getRatingGraph(int userId) {
        try (SqlSession session = sessionFactory.openSession()) {
            
            EntryMapper entryMapper = session.getMapper(EntryMapper.class);
            
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
    }

    public List<Entry> getEntriesPage(int userId, int page, int pageSize) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(EntryMapper.class)
                          .getEntriesPage(userId, page, pageSize);
        }
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
        try (SqlSession session = sessionFactory.openSession(ExecutorType.BATCH)) {
            new RatingUpdater(session).recalcRatings(fromGameId);
        }
    }

    /** Create or update existing game. */
    public void upsertGame(Game game) {
        if (game.getTitle().length() > 100) {
            game.setTitle(game.getTitle().substring(0, 100));
        }
        try (SqlSession session = sessionFactory.openSession(true)) {
            GameMapper gameMapper = session.getMapper(GameMapper.class);
            Game existing = gameMapper.getGame(game.getId());
            if (existing == null) {
                gameMapper.insertGame(game);
            } else {
                gameMapper.updateGame(game);
            }
        }
    }

    /** Create or update existing player. */
    public void upsertUser(Player player) {
        if (player.getDisplayName().length() > 40) {
            player.setDisplayName(player.getDisplayName().substring(0, 40));
        }
        try (SqlSession session = sessionFactory.openSession(true)) {
            PlayerMapper playerMapper = session.getMapper(PlayerMapper.class);
            Player existing = playerMapper.getPlayer(player.getId());
            if (existing == null) {
                playerMapper.insertPlayer(player);
            } else {
                playerMapper.updatePlayer(player);
            }
        }
    }

    /** Create or update existing entry. */
    public void upsertEntry(Entry entry) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            EntryMapper entryMapper = session.getMapper(EntryMapper.class);
            Entry existing = entryMapper.getEntry(entry.getId(), entry.getGameId());
            if (existing == null) {
                entryMapper.insertEntry(entry);
            } else {
                entryMapper.updateEntry(entry);
            }
        }
    }

    public void updateEntry(Entry entry) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getMapper(EntryMapper.class).updateEntry(entry);
        }
    }

    public Entry getEntry(int id, int gameId) {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(EntryMapper.class).getEntry(id, gameId);
        }
    }

    public List<Player> getAllPlayers() {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(PlayerMapper.class).getAllPlayers();
        }
    }

    public List<PlayerListCache.PlayerListEntry> getAllPlayerIdsAndPositions() {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(PlayerMapper.class).getPlayerListIds();
        }
    }

    public List<Player> getUsersByIds(List<Integer> ids) {
        try (SqlSession session = sessionFactory.openSession()) {
            List<Player> players = session.getMapper(PlayerMapper.class).getPlayers(ids);
            // Make sure they are sorted according to ids parameter.
            players.sort(comparing(player -> ids.indexOf(player.getId())));
            return players;
        }
    }
    
    public void updateRepPositions() {
        try (SqlSession session = sessionFactory.openSession(true)) {
            PlayerMapper playerMapper = session.getMapper(PlayerMapper.class);
            int batchSize = 50000;
            int maxPlayerId = playerMapper.getMaxPlayerId();
            Progress progress = new Progress(logger, "Updating rep positions...", maxPlayerId);
            int id = 0;
            while (id <= maxPlayerId) {
                playerMapper.updateRepPositions(id, id + batchSize);
                progress.incProgress(batchSize);
                id += batchSize; // +1 since SQL BETWEEN condition is inclusive.
                try {
                    session.flushStatements();
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        }
    }

    public int getCycleStartGameId() {
        try (SqlSession session = sessionFactory.openSession()) {
            return session.getMapper(GameMapper.class).getCycleStartGameId();
        }
    }

    public void batchUpdateLastVisit(Instant from, Instant to, Instant visitTime) {
        try (SqlSession session = sessionFactory.openSession(true)) {
            session.getMapper(GameMapper.class)
                   .batchUpdateLastVisit(Timestamp.from(from),
                                         Timestamp.from(to),
                                         Timestamp.from(visitTime));
        }
    }
}
