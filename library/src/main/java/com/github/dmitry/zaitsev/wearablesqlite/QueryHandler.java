package com.github.dmitry.zaitsev.wearablesqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.github.dmitry.zaitsev.wearablesqlite.services.ApiConnectionException;
import com.github.dmitry.zaitsev.wearablesqlite.services.GoogleApiUtils;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Responds on SQL query wrapped in {@link com.google.android.gms.wearable.MessageEvent}.
 * <p/>
 * Must be closed after usage.
 */
public class QueryHandler {

    private GoogleApiClient apiClient;
    private SQLiteDatabase database;

    /**
     * Creates new instance of {@link QueryHandler}.
     * {@link QueryHandler} valid until call to {@link #close()}.
     * <p/>
     * All created instances must be closed at some point.
     *
     * @return new instance of {@link com.github.dmitry.zaitsev.wearablesqlite.QueryHandler}
     * @throws com.github.dmitry.zaitsev.wearablesqlite.services.ApiConnectionException if connection
     *                                                                                  to Google API failed.
     */
    public static QueryHandler create(Context context, SQLiteDatabase database) throws ApiConnectionException {
        GoogleApiClient apiClient = GoogleApiUtils.connect(context);

        return new QueryHandler(apiClient, database);
    }

    private QueryHandler(GoogleApiClient apiClient, SQLiteDatabase database) {
        this.apiClient = apiClient;
        this.database = database;
    }

    /**
     * Silently checks input for being an SQL query request.
     * <p/>
     * If SQL query (sent out by {@link com.github.dmitry.zaitsev.wearablesqlite.RemoteSQLAdapter}
     * received, performs actual request to database and sends serialized result back to
     * {@link com.github.dmitry.zaitsev.wearablesqlite.RemoteSQLAdapter}.
     * <p/>
     * If no SQL query detected - does nothing.
     *
     * @param messageEvent event to handle
     */
    public void handleMessage(MessageEvent messageEvent) {
        final String path = messageEvent.getPath();

        if (!path.startsWith(RemoteSQLAdapter.PATH_QUERY_EXECUTOR)) {
            return;
        }

        byte[] data = messageEvent.getData();

        try {
            SerializableCursorPart cursorPart = processQuery(data);

            sendCursorPart(messageEvent.getPath(), cursorPart);
        } catch (IOException e) {
        }
    }

    private void sendCursorPart(String path, SerializableCursorPart cursorPart) throws IOException {
        final byte[] message = SerializationUtils.serialize(cursorPart);
        for (String node : getNodes(apiClient)) {
            Wearable.MessageApi.sendMessage(
                    apiClient,
                    node,
                    path,
                    message
            ).await();
        }
    }

    private Collection<String> getNodes(GoogleApiClient apiClient) {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(apiClient).await();

        if (nodes == null) {
            return Collections.emptySet();
        }

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }
        return results;
    }

    private SerializableCursorPart processQuery(byte[] data) throws IOException {
        SerializableQuery query = SerializationUtils.deserialize(data);

        Cursor cursor = database.rawQuery(query.query, query.args);
        try {
            String[] columns = cursor.getColumnNames();
            Object[][] rows = new Object[cursor.getCount()][columns.length];

            for (int i = 0; cursor.moveToNext(); i++) {
                for (int j = 0; j < columns.length; j++) {
                    rows[i][j] = cursor.getString(j);
                }
            }

            return new SerializableCursorPart(columns, rows);
        } finally {
            cursor.close();
        }
    }

    /**
     * Frees associated resources.
     * {@link com.github.dmitry.zaitsev.wearablesqlite.QueryHandler} can't be used after
     * invocation of this method.
     */
    public void close() {
        apiClient.disconnect();
        apiClient = null;
        database = null;
    }

    /**
     * Closes {@link com.github.dmitry.zaitsev.wearablesqlite.QueryHandler}. Might take {@code null}
     * as an argument.
     */
    public static void closeQuietly(QueryHandler handler) {
        if (handler != null) {
            handler.close();
        }
    }

}
