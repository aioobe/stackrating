package com.stackrating.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Player {

    private int id;
    private String displayName;
    private int rep;
    private double rating;
    private int repPos;
    private int ratingPos;

    public Player(int id,
                  String displayName,
                  int rep,
                  double rating,
                  int repPos,
                  int ratingPos) {
        this.id = id;
        this.displayName = displayName;
        this.rep = rep;
        this.rating = rating;
        this.repPos = repPos;
        this.ratingPos = ratingPos;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRep() {
        return rep;
    }

    public double getRating() {
        return rating;
    }

    public int getRatingPos() {
        return ratingPos;
    }

    public int getRepPos() {
        return repPos;
    }

    @Override
    public String toString() {
        return String.format("%s[id: %d, name: %s]", getClass().getSimpleName(), id, displayName);
    }

    public void setRatingPos(int ratingPos) {
        this.ratingPos = ratingPos;
    }

    public void setRepPos(int repPos) {
        this.repPos = repPos;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setRep(int rep) {
        this.rep = rep;
    }
}
