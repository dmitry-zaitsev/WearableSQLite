package com.github.dmitry.zaitsev.wearablesqlite.services;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

/**
 * Util methods for Google Api
 */
public class GoogleApiUtils {

    /**
     * Performs blocking connect to Google APi
     *
     * @return connected {@link com.google.android.gms.common.api.GoogleApiClient}
     * @throws ApiConnectionException with detail information in case of connection error
     */
    public static GoogleApiClient connect(Context context) throws ApiConnectionException {
        GoogleApiClient client = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();

        ConnectionResult result = client.blockingConnect();

        if (!result.isSuccess()) {
            throw new ApiConnectionException(result);
        }

        return client;
    }

}
