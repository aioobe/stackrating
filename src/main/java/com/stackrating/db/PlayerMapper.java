package com.stackrating.db;

import com.stackrating.model.Player;
import com.stackrating.storage.RatingUpdater.PlayerStateTracker.PlayerState;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;

public interface PlayerMapper {
    Player getPlayer(int id);
    int getNumPlayers();
    void insertPlayer(Player p);
    void updatePlayer(Player p);
    void updateRating(@Param("playerId") int playerId, @Param("rating") double rating);
    void updateRatingPositions(@Param("fromId") int fromId, @Param("toId") int toId);
    int getMaxPlayerId();
    Cursor<Integer> getAllPlayerIds(@Param("orderBy") String orderBy);
    List<Player> getPlayers(@Param("ids") List<Integer> ids);
    void updateRepPositions(@Param("fromId") int fromId, @Param("toId") int toId);
    List<PlayerState> getPlayerStates(@Param("fromGameId") int fromGameId,
                                      @Param("playerIds") List<Integer> playerIds);
}
