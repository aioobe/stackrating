package com.stackrating.storage;

import com.stackrating.db.EntryMapper;
import com.stackrating.db.GameMapper;
import com.stackrating.db.PlayerMapper;
import com.stackrating.elo.EloCalculator;
import com.stackrating.log.Progress;
import com.stackrating.model.Entry;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.stackrating.storage.PagingIterator.getPages;
import static java.util.stream.Collectors.*;

public class RatingUpdater {

    private final static Logger logger = LoggerFactory.getLogger(RatingUpdater.class);

    private final SqlSession session;
    private final PlayerMapper playerMapper;
    private final GameMapper gameMapper;
    private final EntryMapper entryMapper;
    private final PlayerStateTracker playerStates;

    public RatingUpdater(SqlSession session) {
        this.session = session;
        playerMapper = session.getMapper(PlayerMapper.class);
        gameMapper = session.getMapper(GameMapper.class);
        entryMapper = session.getMapper(EntryMapper.class);
        playerStates = new PlayerStateTracker();
    }

    public void recalcRatings(int fromGameId) {
        updateRatingDeltas(fromGameId);
        updatePlayerRatingAndPositions();
    }

    private void updateRatingDeltas(int fromGameId) {
        int batchSize = 1000;
        int maxGameId = gameMapper.getMaxGameId();
        Progress progress = new Progress(logger,
                                         "Recalculating rating deltas...",
                                         maxGameId - fromGameId);
        progress.setProgress(0);
        while (fromGameId <= maxGameId) {
            int toGameId = fromGameId + batchSize;
            recalcRatingsForGameIdRange(fromGameId, toGameId);
            progress.incProgress(batchSize);
            fromGameId = toGameId + 1; // +1 since SQL BETWEEN is inclusive
        }
    }

    /**
     * Update rating deltas in entries for all games between fromGameId (inclusive) to
     * toGameId (inclusive).
     */
    private void recalcRatingsForGameIdRange(int fromGameId,
                                             int toGameId) {
        List<Entry> entries = entryMapper.getEntriesForGames(fromGameId, toGameId);

        // Make sure map is sorted so that games are judged according to game id order.
        SortedMap<Integer, List<Entry>> byGameId = entries.stream()
                .collect(groupingBy(Entry::getGameId, TreeMap::new, toList()));

        // Make sure states of participants are loaded and readily available as input for Elo
        // computations.
        ensurePlayerStatesLoaded(fromGameId, getPlayerIdsFromEntries(entries));

        for (List<Entry> gameEntries : byGameId.values()) {
            recomputeRatingDeltaFields(playerStates, gameEntries);
            gameEntries.forEach(entryMapper::updateEntry);
        }

        // It is "safe" to commit the new rating deltas. Players ratings may not sum up to their
        // rating deltas, but the entry rating deltas are now more accurate.
        // (Right now I don't know if there's any *point* in doing this, but maybe the DBMS
        // needs to hold uncommitted changes in memory?)
        session.commit();
    }

    private void updatePlayerRatingAndPositions() {
        // 1. Update ratings (so we can figure out and update rating_pos without loading all
        //    player ids into memory).
        logger.info("Updating player ratings...");
        Progress progress = new Progress(logger,
                                         "Updating player ratings...",
                                         playerStates.getTrackedPlayerIds().size());
        for (List<Integer> playerIdsPage : getPages(playerStates.getTrackedPlayerIds(), 1000)) {
            for (int id : playerIdsPage) {
                playerMapper.updateRating(id, playerStates.getRating(id));
                progress.incProgress(1);
            }
            session.flushStatements();
        }
        // Free some memory.
        playerStates.dispose();

        // 2. Update rating_pos
        int batchSize = 50000;
        int maxPlayerId = playerMapper.getMaxPlayerId();
        logger.info("Updating rating positions...");
        progress = new Progress(logger, "Updating rating positions...", maxPlayerId);
        int id = 0;
        while (id <= maxPlayerId) {
            playerMapper.updateRatingPositions(id, id + batchSize);
            progress.incProgress(batchSize);
            id += batchSize;
            try {
                session.flushStatements();
            } catch (Exception e) {
                logger.error("Error during update of rating positions. Try with smaller batch size perhaps?", e);
            }
        }

        // Ratings and position fields are in sync. Commit!
        session.commit();
    }

    private void recomputeRatingDeltaFields(PlayerStateTracker playerStates,
                                            List<Entry> gameEntries) {
        // Reset all deltas to 0
        gameEntries.forEach(e -> e.setRatingDelta(0));

        // Ignore game if there's only one participant
        if (gameEntries.size() > 1/* && !game.isDeleted()*/) {
            // Remove entries that are posted more than 90 days after game was initiated
            List<Entry> entriesToJudge = new ArrayList<>(gameEntries);
            entriesToJudge.removeIf(e -> !e.isPostedWithin90daysOfGamePosting());

            // Prepare input for Elo calculation
            List<EloCalculator.PlayerInfo> playerInfos = new ArrayList<>();
            for (Entry entry : entriesToJudge) {
                playerInfos.add(new EloCalculator.PlayerInfo(playerStates.getNumGamesPlayed(entry.getPlayerId()),
                                               playerStates.getRating(entry.getPlayerId()),
                                               entry.getVotes()));
            }

            // Elo computation / rating updates
            double[] ratingDeltas = EloCalculator.computeRatingDeltas(playerInfos);
            for (int i = 0; i < ratingDeltas.length; i++) {
                entriesToJudge.get(i).setRatingDelta(ratingDeltas[i]);
            }
        }

        // Update player states
        for (Entry entry : gameEntries) {
            playerStates.incGamesPlayed(entry.getPlayerId());
            playerStates.addRatingDelta(entry.getPlayerId(), entry.getRatingDelta());
        }
    }

    private void ensurePlayerStatesLoaded(int beforeGameId,
                                          Set<Integer> playerIds) {
        Set<Integer> playerIdsToLoad = new HashSet<>(playerIds);
        playerIdsToLoad.removeAll(playerStates.getTrackedPlayerIds());

        // Pagination below is to limit the size of the constructed query.
        for (List<Integer> playerIdsPage : getPages(playerIdsToLoad, 100)) {
            for (PlayerStateTracker.PlayerState ps : playerMapper.getPlayerStates(beforeGameId, playerIdsPage)) {
                playerStates.initialize(ps);
            }
        }
    }

    private Set<Integer> getPlayerIdsFromEntries(List<Entry> entries) {
        return entries.stream()
                      .map(Entry::getPlayerId)
                      .collect(toSet());
    }


    public static class PlayerStateTracker {

        // Mutable class representing the state of a player during a rating recalc cycle.
        public static class PlayerState {
            public int playerId;
            public int numGamesPlayed;
            public double currentRating;

            public PlayerState(int playerId, int numGamesPlayed, double currentRating) {
                this.playerId = playerId;
                this.numGamesPlayed = numGamesPlayed;
                this.currentRating = currentRating;
            }
        }

        private Map<Integer, PlayerState> playerStates = new HashMap<>();

        public Set<Integer> getTrackedPlayerIds() {
            return playerStates.keySet();
        }

        public double getRating(int playerId) {
            PlayerState ps = playerStates.get(playerId);
            return ps != null ? playerStates.get(playerId).currentRating : 1500;
        }

        public void incGamesPlayed(int playerId) {
            ensureInitialized(playerId);
            playerStates.get(playerId).numGamesPlayed++;
        }

        public void addRatingDelta(int playerId, double ratingDelta) {
            ensureInitialized(playerId);
            playerStates.get(playerId).currentRating += ratingDelta;
        }

        private void ensureInitialized(int playerId) {
            playerStates.computeIfAbsent(playerId, id -> new PlayerState(id, 0, 1500));
        }

        public int getNumGamesPlayed(int playerId) {
            PlayerState ps = playerStates.get(playerId);
            return ps != null ? playerStates.get(playerId).numGamesPlayed : 0;
        }

        public void initialize(PlayerState ps) {
            playerStates.put(ps.playerId, ps);
        }

        public void dispose() {
            playerStates = null;
        }
    }

}
