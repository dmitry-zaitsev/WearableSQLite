WearableSQLite
==============

Wrapper for Wearable APIs to work with SQLite database across device boundaries. Like run queries from Wearable on Handheld device.

Minimum working example is not yet available due to absence of Wearable support in Maven plugin.

Usage
=====

Library consists of two primary parts: `RemoteSQLAdapter` and `QueryHandler`.

`RemoteSQLAdapter` resides on device which needs the result of SQL query (typically that it is Wearable device). Usage is straightforward and mimics output of `SQLiteDatabase#rawQuery` method:

    RemoteSQLAdapter adapter = null;
    try {
        adapter = RemoteSQLAdapter.create(NotificationActivity.this);
        
        Cursor cursor = adapter.query("SELECT * FROM spending", new String[]{});
        
        //... any operations on Cursor ...
    } catch (ApiConnectionException e) {
        e.printStackTrace();
    } finally {
        RemoteSQLAdapter.closeQuietly(adapter);
    }
    
Query is sent using Wearable Messaging API and processed by `QueryHandler` on device which is responsible for performing SQL query on actual database.

`QueryHandler` typically used on Handheld device and used either in `WearableListenerService` or `MessageApi.MessageListener`. Just feed it all incoming `MessageEvent` as follows:

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        QueryHandler handler = null;
        try {
            handler = QueryHandler.create(context, sqliteDatabase);
            handler.handleMessage(messageEvent);
        } catch (ApiConnectionException e) {
            e.printStackTrace();
        } finally {
            QueryHandler.closeQuietly(handler);
        }
    }
    
Note
----

Take into account that data is sent across device boundaries, so try to avoid queries which might produce large set of rows. Consider using `LIMIT` clause in your SQL queries.

Maven
=====

Project is not yet deployed to public Maven repository, so it should be installed manually. Make sure that you got Google Play Services and Android 4.4W from https://github.com/mosabua/maven-android-sdk-deployer

    <dependency>
        <groupId>com.github.dmitry-zaitsev.wearable-sqlite</groupId>
        <artifactId>library</artifactId>
        <version>1.0.0</version>
    </dependency>
