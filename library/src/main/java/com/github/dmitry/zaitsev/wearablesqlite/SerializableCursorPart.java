package com.github.dmitry.zaitsev.wearablesqlite;

import java.io.Serializable;

/**
 * Partial or full data from {@link android.database.Cursor}
 */
public class SerializableCursorPart implements Serializable {

    public final String[] columns;
    public final Object[][] rows;

    public SerializableCursorPart(String[] columns, Object[][] rows) {
        this.columns = columns;
        this.rows = rows;
    }

}
