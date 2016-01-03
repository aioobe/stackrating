package com.stackrating.db;

import com.stackrating.model.Game;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;

public interface GameMapper {
    Game getGame(@Param("id") int id);
    void insertGame(Game game);
    void updateGame(Game game);
    int getMaxGameId();
    int getCycleStartGameId();
    void batchUpdateLastVisit(@Param("from") Timestamp from, @Param("to") Timestamp to, @Param("lastVisit") Timestamp lastVisit);
}
