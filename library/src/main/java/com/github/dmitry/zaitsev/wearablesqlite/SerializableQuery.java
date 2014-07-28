package com.github.dmitry.zaitsev.wearablesqlite;

import java.io.Serializable;

/**
 * SQL query to be executed
 */
public class SerializableQuery implements Serializable {

    public final String query;
    public final String[] args;

    public SerializableQuery(String query, String[] args) {
        this.query = query;
        this.args = args;
    }

}
