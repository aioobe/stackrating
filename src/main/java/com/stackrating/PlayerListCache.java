package com.stackrating;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.stackrating.Main.SortingPolicy.BY_RATING;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/** Used to keep rep/rating positions in memory for fast user list page retrieval. */
public class PlayerListCache {
    
    public static class PlayerListEntry {
        public int id;
        public int repPos;
        public int ratingPos;

        public PlayerListEntry(int id, int repPos, int ratingPos) {
            this.id = id;
            this.repPos = repPos;
            this.ratingPos = ratingPos;
        }

        public int getId() {
            return id;
        }

        public int getRepPos() {
            return repPos;
        }

        public int getRatingPos() {
            return ratingPos;
        }
    }
    
    int[] repList;
    int[] ratingList;
    
    void setEntries(List<PlayerListEntry> entries) {
        int[] newRepList = entries.stream()
                                  .sorted(comparing(PlayerListEntry::getRepPos))
                                  .mapToInt(PlayerListEntry::getId)
                                  .toArray();

        int[] newRatingList = entries.stream()
                                     .sorted(comparing(PlayerListEntry::getRatingPos))
                                     .mapToInt(PlayerListEntry::getId)
                                     .toArray();
        repList = newRepList;
        ratingList = newRatingList;
    }

    /** {@code page} is a 1-based value */ 
    List<Integer> getIdsForPage(int page, int pageSize, Main.SortingPolicy sortingPolicy) {
        int[] list = sortingPolicy == BY_RATING ? ratingList : repList;
        int fromIndex = (page - 1) * pageSize;
        int to = Math.min(list.length, fromIndex + pageSize);
        int[] idsArr = Arrays.copyOfRange(list, fromIndex, to);
        return IntStream.of(idsArr).boxed().collect(toList());
    }

    public int getUserCount() {
        return repList.length;
    }
}
