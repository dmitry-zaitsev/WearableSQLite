package com.github.dmitry.zaitsev.wearablesqlite.services;

import com.google.android.gms.common.ConnectionResult;

/**
 * Thrown when connection to Google Play Services can't be established
 */
public class ApiConnectionException extends Exception {

    private final ConnectionResult connectionResult;

    public ApiConnectionException(ConnectionResult result) {
        super("Connection error: " + result.getErrorCode());

        this.connectionResult = result;
    }

    /**
     * @return {@link com.google.android.gms.common.ConnectionResult} of last attempt to connect to
     * Google API.
     */
    public ConnectionResult getConnectionResult() {
        return connectionResult;
    }

}
