package com.stackrating.db;

import com.stackrating.model.Entry;
import com.stackrating.model.TimeDataPoint;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EntryMapper {
    int getEntryCountForUser(@Param("userId") int userId);
    List<Entry> getEntriesPage(@Param("userId") int userId,
                               @Param("page") int page,
                               @Param("pageSize") int pageSize);
    List<TimeDataPoint> getRatingDeltas(@Param("userId") int userId);
    List<Entry> getEntriesForGame(@Param("gameId") int gameId);
    List<Entry> getEntriesForGames(@Param("fromGameId") int fromGameId, @Param("toGameId") int toGameId);

    Entry getEntry(@Param("id") int id, @Param("gameId") int gameId);
    void insertEntry(Entry entry);
    void updateEntry(Entry entry);
}
