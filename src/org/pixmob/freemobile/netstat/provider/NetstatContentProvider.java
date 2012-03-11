/*
 * Copyright (C) 2012 Pixmob (http://github.com/pixmob)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixmob.freemobile.netstat.provider;

import static org.pixmob.freemobile.netstat.Constants.TAG;

import java.util.ArrayList;

import org.pixmob.freemobile.netstat.provider.NetstatContract.PhoneEvents;
import org.pixmob.freemobile.netstat.provider.NetstatContract.WifiEvents;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * The content provider for the application database.
 * @author Pixmob
 */
public class NetstatContentProvider extends ContentProvider {
    private static final String PHONE_EVENTS_TABLE = "phone_events";
    private static final String WIFI_EVENTS_TABLE = "wifi_events";
    
    private static final int PHONE_EVENTS = 1;
    private static final int PHONE_EVENT_ID = 2;
    private static final int WIFI_EVENTS = 3;
    private static final int WIFI_EVENT_ID = 4;
    
    private static final UriMatcher URI_MATCHER;
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(NetstatContract.AUTHORITY, "phoneEvents",
            PHONE_EVENTS);
        URI_MATCHER.addURI(NetstatContract.AUTHORITY, "phoneEvent/*",
            PHONE_EVENT_ID);
        URI_MATCHER
                .addURI(NetstatContract.AUTHORITY, "wifiEvents", WIFI_EVENTS);
        URI_MATCHER.addURI(NetstatContract.AUTHORITY, "wifiEvent/*",
            WIFI_EVENT_ID);
    }
    
    private SQLiteOpenHelper dbHelper;
    
    @Override
    public boolean onCreate() {
        try {
            dbHelper = new DatabaseHelper(getContext());
        } catch (Exception e) {
            Log.e(TAG, "Cannot create content provider", e);
            return false;
        }
        return true;
    }
    
    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case PHONE_EVENTS:
                return PhoneEvents.CONTENT_TYPE;
            case PHONE_EVENT_ID:
                return PhoneEvents.CONTENT_ITEM_TYPE;
            case WIFI_EVENTS:
                return WifiEvents.CONTENT_TYPE;
            case WIFI_EVENT_ID:
                return WifiEvents.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
    }
    
    @Override
    public ContentProviderResult[] applyBatch(
            ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        if (operations.isEmpty()) {
            return new ContentProviderResult[0];
        }
        
        // Execute batch operations in a single transaction for performance.
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
        }
    }
    
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final String table;
        final Uri contentUri;
        switch (URI_MATCHER.match(uri)) {
            case PHONE_EVENTS:
                table = PHONE_EVENTS_TABLE;
                contentUri = PhoneEvents.CONTENT_URI;
                break;
            case WIFI_EVENTS:
                table = WIFI_EVENTS_TABLE;
                contentUri = WifiEvents.CONTENT_URI;
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final long rowId = db.insertOrThrow(table, "notNull", values);
        if (rowId == -1) {
            throw new SQLException("Failed to insert new row");
        }
        
        final Uri rowUri = Uri.withAppendedPath(contentUri,
            String.valueOf(rowId));
        getContext().getContentResolver().notifyChange(uri, null, false);
        
        return rowUri;
    }
    
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int count;
        
        switch (URI_MATCHER.match(uri)) {
            case PHONE_EVENTS:
                count = db.delete(PHONE_EVENTS_TABLE, selection, selectionArgs);
                break;
            case PHONE_EVENT_ID:
                final String phoneId = uri.getPathSegments().get(1);
                String phoneFullSelection = PhoneEvents._ID + "='" + phoneId
                        + "'";
                if (!TextUtils.isEmpty(selection)) {
                    phoneFullSelection += " AND (" + selection + ")";
                }
                count = db.delete(PHONE_EVENTS_TABLE, phoneFullSelection,
                    selectionArgs);
                break;
            case WIFI_EVENTS:
                count = db.delete(WIFI_EVENTS_TABLE, selection, selectionArgs);
                break;
            case WIFI_EVENT_ID:
                final String wifiId = uri.getPathSegments().get(1);
                String wifiFullSelection = WifiEvents._ID + "='" + wifiId + "'";
                if (!TextUtils.isEmpty(selection)) {
                    wifiFullSelection += " AND (" + selection + ")";
                }
                count = db.delete(WIFI_EVENTS_TABLE, wifiFullSelection,
                    selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String realSortOrder = sortOrder;
        
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (URI_MATCHER.match(uri)) {
            case PHONE_EVENTS:
                qb.setTables(PHONE_EVENTS_TABLE);
                if (TextUtils.isEmpty(realSortOrder)) {
                    realSortOrder = PhoneEvents.TIMESTAMP + " DESC";
                }
                break;
            case PHONE_EVENT_ID:
                qb.setTables(PHONE_EVENTS_TABLE);
                qb.appendWhere(PhoneEvents._ID + "="
                        + uri.getPathSegments().get(1));
                break;
            case WIFI_EVENTS:
                qb.setTables(WIFI_EVENTS_TABLE);
                if (TextUtils.isEmpty(realSortOrder)) {
                    realSortOrder = WifiEvents.TIMESTAMP + " DESC";
                }
                break;
            case WIFI_EVENT_ID:
                qb.setTables(WIFI_EVENTS_TABLE);
                qb.appendWhere(WifiEvents._ID + "="
                        + uri.getPathSegments().get(1));
                break;
        }
        
        final SQLiteDatabase db = dbHelper.getReadableDatabase();
        final Cursor c = qb.query(db, projection, selection, selectionArgs,
            null, null, realSortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        
        return c;
    }
    
    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int count;
        switch (URI_MATCHER.match(uri)) {
            case PHONE_EVENTS:
                count = db.update(PHONE_EVENTS_TABLE, values, selection,
                    selectionArgs);
                break;
            case PHONE_EVENT_ID:
                final String phoneId = uri.getPathSegments().get(1);
                String phoneFullSelection = PhoneEvents._ID + "='" + phoneId
                        + "'";
                if (!TextUtils.isEmpty(selection)) {
                    phoneFullSelection += " AND (" + selection + ")";
                }
                count = db.update(PHONE_EVENTS_TABLE, values,
                    phoneFullSelection, selectionArgs);
                break;
            case WIFI_EVENTS:
                count = db.update(WIFI_EVENTS_TABLE, values, selection,
                    selectionArgs);
                break;
            case WIFI_EVENT_ID:
                final String wifiId = uri.getPathSegments().get(1);
                String wifiFullSelection = PhoneEvents._ID + "='" + wifiId
                        + "'";
                if (!TextUtils.isEmpty(selection)) {
                    wifiFullSelection += " AND (" + selection + ")";
                }
                count = db.update(WIFI_EVENTS_TABLE, values, wifiFullSelection,
                    selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
    
    /**
     * This class is responsible for managing the application database. The
     * database schema is initialized when the it is created, and upgraded after
     * an application update.
     * @author Pixmob
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(final Context context) {
            super(context, "netstat.db", null, 1);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            if (!db.isReadOnly()) {
                String req = "CREATE TABLE " + PHONE_EVENTS_TABLE + " ("
                        + PhoneEvents._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + PhoneEvents.TIMESTAMP + " TIMESTAMP NOT NULL, "
                        + PhoneEvents.MOBILE_CONNECTED + " INTEGER NOT NULL, "
                        + PhoneEvents.SYNC_ID + " TEXT NOT NULL, "
                        + PhoneEvents.SYNC_STATUS + " INTEGER NOT NULL, "
                        + PhoneEvents.MOBILE_OPERATOR + " TEXT)";
                db.execSQL(req);
                
                req = "CREATE TABLE " + WIFI_EVENTS_TABLE + " ("
                        + PhoneEvents._ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + PhoneEvents.TIMESTAMP + " TIMESTAMP NOT NULL, "
                        + WifiEvents.WIFI_CONNECTED + " INTEGER NOT NULL, "
                        + PhoneEvents.SYNC_ID + " TEXT NOT NULL, "
                        + PhoneEvents.SYNC_STATUS + " INTEGER NOT NULL)";
                db.execSQL(req);
            }
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (!db.isReadOnly()) {
                Log.w(TAG, "Upgrading database from version " + oldVersion
                        + " to " + newVersion + " which will destroy all data");
                db.execSQL("DROP TABLE IF EXISTS " + PHONE_EVENTS_TABLE);
                db.execSQL("DROP TABLE IF EXISTS " + WIFI_EVENTS_TABLE);
                onCreate(db);
            }
        }
    }
}
