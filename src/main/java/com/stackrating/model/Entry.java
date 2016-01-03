package com.stackrating.model;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;

public class Entry {

    private int id;
    private int playerId;
    private int gameId;
    private int votes;
    private Timestamp postTime;
    private double ratingDelta;

    // Auxiliary data (not always set)
    private String gameTitle;
    private String userDisplayName;
    private Timestamp gamePostTime;

    public Entry(int id,
                 int playerId,
                 int gameId,
                 int votes,
                 Timestamp postTime,
                 double ratingDelta,
                 String gameTitle,
                 String userDisplayName,
                 Timestamp gamePostTime) {
        this.id = id;
        this.playerId = playerId;
        this.gameId = gameId;
        this.votes = votes;
        this.postTime = postTime;
        this.ratingDelta = ratingDelta;
        this.gameTitle = gameTitle;
        this.userDisplayName = userDisplayName;
        this.gamePostTime = gamePostTime;
    }

    public int getId() {
        return id;
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getGameId() {
        return gameId;
    }

    public int getVotes() {
        return votes;
    }

    public Timestamp getPostTime() {
        return postTime;
    }

    public String getGameTitle() {
        return gameTitle;
    }

    public double getRatingDelta() {
        return ratingDelta;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public boolean isPostedWithin90daysOfGamePosting() {
        return (postTime.getTime() - gamePostTime.getTime()) < (90 * 24 * 60 * 60 * 1000L);
//        return postTime.isBefore(gamePostTime.plus(90, ChronoUnit.DAYS));
    }

    public void setRatingDelta(double ratingDelta) {
        this.ratingDelta = ratingDelta;
    }

    public void setVotes(int votes) {
        this.votes = votes;
    }

    @Override
    public String toString() {
        return String.format("%s[id: %d, playerId: %d, gameId: %d, ratingDelta: %+.2f",
                             getClass().getSimpleName(), id, playerId, gameId, ratingDelta);
    }
}
