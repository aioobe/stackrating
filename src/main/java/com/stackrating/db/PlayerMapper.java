package com.stackrating.db;

import com.stackrating.PlayerListCache;
import com.stackrating.model.Player;
import com.stackrating.storage.RatingUpdater.PlayerStateTracker.PlayerState;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PlayerMapper {
    Player getPlayer(int id);
    int getNumPlayers();
    void insertPlayer(Player p);
    void updatePlayer(Player p);
    List<PlayerState> getPlayerStates(@Param("fromGameId") int fromGameId, @Param("playerIds") List<Integer> playerIds);
    void updateRating(@Param("playerId") int playerId, @Param("rating") double rating);
    void updateRatingPositions(@Param("fromId") int fromId, @Param("toId") int toId);
    int getMaxPlayerId();
    List<Player> getAllPlayers();
    List<PlayerListCache.PlayerListEntry> getPlayerListIds();
    List<Player> getPlayers(@Param("ids") List<Integer> ids);
    void updateRepPositions(@Param("fromId") int fromId, @Param("toId") int toId);
}
