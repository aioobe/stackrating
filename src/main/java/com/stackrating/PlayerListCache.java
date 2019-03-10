package com.stackrating;

import com.stackrating.storage.Storage;
import org.apache.ibatis.cursor.Cursor;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Used to keep rep/rating positions in memory for fast user list page retrieval. */
public class PlayerListCache {

    private final static Path REP_CACHE_FILE = Paths.get("repListCache.dat");
    private final static Path RATING_CACHE_FILE = Paths.get("ratingListCache.dat");

    private final Storage storage;
    private int userCount;

    public PlayerListCache(Storage storage) {
        this.storage = storage;
    }

    private Path cacheFile(SortingPolicy sortingPolicy) {
        switch (sortingPolicy) {
            case BY_RATING: return RATING_CACHE_FILE;
            case BY_REPUTATION: return REP_CACHE_FILE;
            default: throw new AssertionError("Unknown sorting policy.");
        }
    }

    public void regenerateCache() {
        regenerateCacheHelper(SortingPolicy.BY_RATING);
        regenerateCacheHelper(SortingPolicy.BY_REPUTATION);
    }

    private void regenerateCacheHelper(SortingPolicy sortingPolicy) {
        userCount = 0;
        File cacheFile = cacheFile(sortingPolicy).toFile();
        try (Closeable ac = storage.openSession();
             FileOutputStream fos = new FileOutputStream(cacheFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             DataOutputStream dos = new DataOutputStream(bos);
             Cursor<Integer> ratingIter = storage.getAllPlayerIds(sortingPolicy)) {
            for (int id : ratingIter) {
                userCount++;
                dos.writeInt(id);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** {@code page} is a 1-based value */
    List<Integer> getIdsForPage(int pageNum, int pageSize, SortingPolicy sortingPolicy) {
        List<Integer> page = new ArrayList<>(pageSize);
        File cacheFile = cacheFile(sortingPolicy).toFile();
        try (FileInputStream fis = new FileInputStream(cacheFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             DataInputStream dis = new DataInputStream(bis)) {
            dis.skip((pageNum - 1) * pageSize * 4);
            try {
                while (page.size() < pageSize) {
                    page.add(dis.readInt());
                }
            } catch (EOFException e) {
                // Last page may may have less than pageSize entries.
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return page;
    }

    public int getUserCount() {
        return userCount;
    }
}
