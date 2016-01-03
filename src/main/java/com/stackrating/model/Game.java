package com.stackrating.model;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Game {

    private int id;
    private String title;
    private Timestamp postTime;
    private Timestamp lastVisit;

    public Game(int id,
                String title,
                Timestamp postTime,
                Timestamp lastVisit) {
        this.id = id;
        this.postTime = postTime;
        this.title = title;
        this.lastVisit = lastVisit;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Timestamp getPostTime() {
        return postTime;
    }

    public Timestamp getLastVisit() {
        return lastVisit;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
