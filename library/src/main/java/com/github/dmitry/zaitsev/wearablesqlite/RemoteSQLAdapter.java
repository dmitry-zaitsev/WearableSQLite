package com.github.dmitry.zaitsev.wearablesqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;

import com.github.dmitry.zaitsev.wearablesqlite.services.ApiConnectionException;
import com.github.dmitry.zaitsev.wearablesqlite.services.GoogleApiUtils;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.apache.commons.lang3.SerializationUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allows to perform query on remote SQL database.
 * <p/>
 * Sends messages through Messaging API to all connected nodes. Node, that will provide response to
 * SQL query should feed {@link com.google.android.gms.wearable.MessageEvent} received in
 * {@link com.google.android.gms.wearable.WearableListenerService#onMessageReceived(com.google.android.gms.wearable.MessageEvent)}
 * to {@link com.github.dmitry.zaitsev.wearablesqlite.QueryHandler} in order to respond on query.
 * <p/>
 * Note, that not more than one connected node should respond to query.
 * <p/>
 * Single {@link RemoteSQLAdapter} might be used to perform several
 * queries and must be closed after usage.
 */
public class RemoteSQLAdapter implements MessageApi.MessageListener {

    public static final String PATH_QUERY_EXECUTOR = "/com/dmitry/zaitsev/query";

    private static final String PATH_WITH_ID = PATH_QUERY_EXECUTOR + "/%d";
    private static final long TIMEOUT_MILLIS = 10000L;

    private static final AtomicInteger queryIdCounter = new AtomicInteger(0);
    private static final Map<String, byte[]> responses = Collections.synchronizedMap(
            new HashMap<String, byte[]>()
    );

    private GoogleApiClient apiClient;
    private boolean closed = false;

    /**
     * Creates new instance of {@link RemoteSQLAdapter}.
     * {@link RemoteSQLAdapter} valid until call to {@link #close()}.
     * <p/>
     * All created instances must be closed at some point.
     *
     * @return new instance of {@link RemoteSQLAdapter}
     * @throws com.github.dmitry.zaitsev.wearablesqlite.services.ApiConnectionException if connection
     *                                                                                  to Google API failed.
     */
    public static RemoteSQLAdapter create(Context context) throws ApiConnectionException {
        GoogleApiClient client = GoogleApiUtils.connect(context);

        return new RemoteSQLAdapter(client);
    }

    private RemoteSQLAdapter(GoogleApiClient apiClient) {
        this.apiClient = apiClient;
        Wearable.MessageApi.addListener(apiClient, this);
    }

    /**
     * Performs SQL query on remote SQLite database (typically stored on handheld device).
     * <p/>
     * Note that this operation might operate across device boundaries, so try to avoid queries
     * which might return large set of data. Consider using {@code LIMIT} clause within SQL query.
     *
     * @param query query to perform
     * @param args  binding query arguments
     * @return {@link android.database.Cursor} with data
     */
    public synchronized Cursor query(String query, String[] args) {
        ensureNotClosed();

        final int queryId = queryIdCounter.getAndIncrement();
        final String path = String.format(PATH_WITH_ID, queryId);

        sendQuery(path, query, args);
        byte[] response = awaitResponse(path);

        return deserializeCursor(response);
    }

    private void sendQuery(String path, String query, String[] args) {
        SerializableQuery serializableQuery = new SerializableQuery(query, args);

        byte[] message = SerializationUtils.serialize(serializableQuery);

        for (String node : getNodes()) {
            Wearable.MessageApi.sendMessage(
                    apiClient,
                    node,
                    path,
                    message
            ).await();
        }
    }

    private Collection<String> getNodes() {
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

    private byte[] awaitResponse(String path) {
        synchronized (responses) {
            while (!responses.containsKey(path)) {
                try {
                    responses.wait(TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    return null;
                }
            }

            return responses.remove(path);
        }
    }

    private Cursor deserializeCursor(byte[] response) {
        if (response == null) {
            return null;
        }

        SerializableCursorPart cursorPart = SerializationUtils.deserialize(response);
        MatrixCursor matrixCursor = new MatrixCursor(cursorPart.columns);
        for (Object[] row : cursorPart.rows) {
            matrixCursor.addRow(row);
        }

        return matrixCursor;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if (!messageEvent.getPath().startsWith(PATH_QUERY_EXECUTOR)) {
            return;
        }

        synchronized (responses) {
            responses.put(messageEvent.getPath(), messageEvent.getData());
            responses.notifyAll();
        }
    }

    /**
     * Frees associated resources.
     * {@link com.github.dmitry.zaitsev.wearablesqlite.RemoteSQLAdapter} can't be used after
     * invocation of this method.
     */
    public synchronized void close() {
        closed = true;
        Wearable.MessageApi.removeListener(apiClient, this);
        apiClient.disconnect();
        apiClient = null;
    }

    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
    }

}
