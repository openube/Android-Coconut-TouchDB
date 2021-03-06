/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.touchdb.testapp.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;
import android.test.AndroidTestCase;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDQueryOptions;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.TDView;
import com.couchbase.touchdb.TDView.TDViewCollation;
import com.couchbase.touchdb.TDViewMapBlock;
import com.couchbase.touchdb.TDViewMapEmitBlock;
import com.couchbase.touchdb.TDViewReduceBlock;

public class Views extends AndroidTestCase {

    public static final String TAG = "Views";

    public void testViewCreation() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        Assert.assertNull(db.getExistingViewNamed("aview"));

        TDView view = db.getViewNamed("aview");
        Assert.assertNotNull(view);
        Assert.assertEquals(db, view.getDb());
        Assert.assertEquals("aview", view.getName());
        Assert.assertNull(view.getMapBlock());
        Assert.assertEquals(view, db.getExistingViewNamed("aview"));

        boolean changed = view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertTrue(changed);
        Assert.assertEquals(1, db.getAllViews().size());
        Assert.assertEquals(view, db.getAllViews().get(0));

        changed = view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                //no-op
            }
        }, null, "1");

        Assert.assertFalse(changed);

        changed = view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                //no-op
            }
        }, null, "2");

        Assert.assertTrue(changed);

        db.close();
    }

    private TDRevision putDoc(TDDatabase db, Map<String,Object> props) {
        TDRevision rev = new TDRevision(props);
        TDStatus status = new TDStatus();
        rev = db.putRevision(rev, null, false, status);
        Assert.assertTrue(status.isSuccessful());
        return rev;
    }

    public List<TDRevision> putDocs(TDDatabase db) {
        List<TDRevision> result = new ArrayList<TDRevision>();

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("_id", "22222");
        dict2.put("key", "two");
        result.add(putDoc(db, dict2));

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("_id", "44444");
        dict4.put("key", "four");
        result.add(putDoc(db, dict4));

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("_id", "11111");
        dict1.put("key", "one");
        result.add(putDoc(db, dict1));

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("_id", "33333");
        dict3.put("key", "three");
        result.add(putDoc(db, dict3));

        Map<String,Object> dict5 = new HashMap<String,Object>();
        dict5.put("_id", "55555");
        dict5.put("key", "five");
        result.add(putDoc(db, dict5));

        return result;
    }

    public void putNDocs(TDDatabase db, int n) {
        for(int i=0; i< n; i++) {
            Map<String,Object> doc = new HashMap<String,Object>();
            doc.put("_id", String.format("%d", i));
            List<String> key = new ArrayList<String>();
            for(int j=0; j< 256; j++) {
                key.add("key");
            }
            key.add(String.format("key-%d", i));
            doc.put("key", key);
            putDoc(db, doc);
        }
    }

    public static TDView createView(TDDatabase db) {
        TDView view = db.getViewNamed("aview");
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if(document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }
        }, null, "1");
        return view;
    }

    public void testViewIndex() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("key", "one");
        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("key", "two");
        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("key", "three");
        Map<String,Object> dictX = new HashMap<String,Object>();
        dictX.put("clef", "quatre");

        TDRevision rev1 = putDoc(db, dict1);
        TDRevision rev2 = putDoc(db, dict2);
        TDRevision rev3 = putDoc(db, dict3);
        putDoc(db, dictX);

        TDView view = createView(db);

        Assert.assertEquals(1, view.getViewId());
        Assert.assertTrue(view.isStale());

        TDStatus updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        List<Map<String,Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(1, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"two\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(2, dumpResult.get(2).get("seq"));
        Assert.assertEquals("\"three\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(3, dumpResult.get(1).get("seq"));

        //no-op reindex
        Assert.assertFalse(view.isStale());
        updated = view.updateIndex();
        Assert.assertEquals(TDStatus.NOT_MODIFIED, updated.getCode());

        // Now add a doc and update a doc:
        TDRevision threeUpdated = new TDRevision(rev3.getDocId(), rev3.getRevId(), false);
        Map<String,Object> newdict3 = new HashMap<String,Object>();
        newdict3.put("key", "3hree");
        threeUpdated.setProperties(newdict3);
        TDStatus status = new TDStatus();
        rev3 = db.putRevision(threeUpdated, rev3.getRevId(), false, status);
        Assert.assertTrue(status.isSuccessful());

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("key", "four");
        TDRevision rev4 = putDoc(db, dict4);

        TDRevision twoDeleted = new TDRevision(rev2.getDocId(), rev2.getRevId(), true);
        db.putRevision(twoDeleted, rev2.getRevId(), false, status);
        Assert.assertTrue(status.isSuccessful());

        // Reindex again:
        Assert.assertTrue(view.isStale());
        updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"one\"", dumpResult.get(2).get("key"));
        Assert.assertEquals(1, dumpResult.get(2).get("seq"));
        Assert.assertEquals("\"3hree\"", dumpResult.get(0).get("key"));
        Assert.assertEquals(5, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"four\"", dumpResult.get(1).get("key"));
        Assert.assertEquals(6, dumpResult.get(1).get("seq"));

        // Now do a real query:
        List<Map<String,Object>> rows = view.queryWithOptions(null, status);
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals("one", rows.get(2).get("key"));
        Assert.assertEquals(rev1.getDocId(), rows.get(2).get("id"));
        Assert.assertEquals("3hree", rows.get(0).get("key"));
        Assert.assertEquals(rev3.getDocId(), rows.get(0).get("id"));
        Assert.assertEquals("four", rows.get(1).get("key"));
        Assert.assertEquals(rev4.getDocId(), rows.get(1).get("id"));

        view.removeIndex();

        db.close();
    }

    public void testViewQuery() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        putDocs(db);
        TDView view = createView(db);

        TDStatus updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        // Query all rows:
        TDQueryOptions options = new TDQueryOptions();
        TDStatus status = new TDStatus();
        List<Map<String, Object>> rows = view.queryWithOptions(options, status);

        List<Object> expectedRows = new ArrayList<Object>();

        Map<String,Object> dict5 = new HashMap<String,Object>();
        dict5.put("id", "55555");
        dict5.put("key", "five");
        expectedRows.add(dict5);

        Map<String,Object> dict4 = new HashMap<String,Object>();
        dict4.put("id", "44444");
        dict4.put("key", "four");
        expectedRows.add(dict4);

        Map<String,Object> dict1 = new HashMap<String,Object>();
        dict1.put("id", "11111");
        dict1.put("key", "one");
        expectedRows.add(dict1);

        Map<String,Object> dict3 = new HashMap<String,Object>();
        dict3.put("id", "33333");
        dict3.put("key", "three");
        expectedRows.add(dict3);

        Map<String,Object> dict2 = new HashMap<String,Object>();
        dict2.put("id", "22222");
        dict2.put("key", "two");
        expectedRows.add(dict2);

        Assert.assertEquals(expectedRows, rows);

        // Start/end key query:
        options = new TDQueryOptions();
        options.setStartKey("a");
        options.setEndKey("one");

        status = new TDStatus();
        rows = view.queryWithOptions(options, status);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);
        expectedRows.add(dict1);

        Assert.assertEquals(expectedRows, rows);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        status = new TDStatus();
        rows = view.queryWithOptions(options, status);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict5);
        expectedRows.add(dict4);

        Assert.assertEquals(expectedRows, rows);

        // Reversed:
        options.setDescending(true);
        options.setStartKey("o");
        options.setEndKey("five");
        options.setInclusiveEnd(true);

        status = new TDStatus();
        rows = view.queryWithOptions(options, status);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict5);

        Assert.assertEquals(expectedRows, rows);

        // Reversed, no inclusive end:
        options.setInclusiveEnd(false);

        status = new TDStatus();
        rows = view.queryWithOptions(options, status);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);

        Assert.assertEquals(expectedRows, rows);

        // Specific keys:
        options = new TDQueryOptions();
        List<Object> keys = new ArrayList<Object>();
        keys.add("two");
        keys.add("four");
        options.setKeys(keys);

        rows = view.queryWithOptions(options, status);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(dict4);
        expectedRows.add(dict2);

        Assert.assertEquals(expectedRows, rows);

        db.close();
    }

    public void testAllDocsQuery() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        List<TDRevision> docs = putDocs(db);

        List<Map<String,Object>> expectedRow = new ArrayList<Map<String,Object>>();
        for (TDRevision rev : docs) {
            Map<String,Object> value = new HashMap<String, Object>();
            value.put("rev", rev.getRevId());

            Map<String, Object> row = new HashMap<String, Object>();
            row.put("id", rev.getDocId());
            row.put("key", rev.getDocId());
            row.put("value", value);
            expectedRow.add(row);
        }

        TDQueryOptions options = new TDQueryOptions();
        Map<String,Object> query = db.getAllDocs(options);

        List<Object>expectedRows = new ArrayList<Object>();
        expectedRows.add(expectedRow.get(2));
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));
        expectedRows.add(expectedRow.get(4));

        Map<String,Object> expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Start/end key query:
        options = new TDQueryOptions();
        options.setStartKey("2");
        options.setEndKey("44444");

        query = db.getAllDocs(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));
        expectedRows.add(expectedRow.get(1));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Start/end query without inclusive end:
        options.setInclusiveEnd(false);

        query = db.getAllDocs(options);

        expectedRows = new ArrayList<Object>();
        expectedRows.add(expectedRow.get(0));
        expectedRows.add(expectedRow.get(3));

        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Get specific documents:
        options = new TDQueryOptions();
        query = db.getDocsWithIDs(new ArrayList<String>(), options);
        expectedQueryResult = createExpectedQueryResult(new ArrayList<Object>(), 0);
        Assert.assertEquals(expectedQueryResult, query);

        // Get specific documents:
        options = new TDQueryOptions();
        List<String> docIds = new ArrayList<String>();
        Map<String,Object> expected2 = expectedRow.get(2);
        docIds.add((String)expected2.get("id"));
        query = db.getDocsWithIDs(docIds, options);
        expectedRows = new ArrayList<Object>();
        expectedRows.add(expected2);
        expectedQueryResult = createExpectedQueryResult(expectedRows, 0);
        Assert.assertEquals(expectedQueryResult, query);

        db.close();
    }

    private Map<String, Object> createExpectedQueryResult(List<Object> rows, int offset) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("total_rows", rows.size());
        result.put("offset", offset);
        return result;
    }

    public void testViewReduce() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "CD");
        docProperties1.put("cost", 8.99);
        putDoc(db, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "App");
        docProperties2.put("cost", 1.95);
        putDoc(db, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "Dessert");
        docProperties3.put("cost", 6.50);
        putDoc(db, docProperties3);

        TDView view = db.getViewNamed("totaler");
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                Object cost = document.get("cost");
                if(cost != null) {
                    emitter.emit(document.get("_id"), cost);
                }
            }
        }, new TDViewReduceBlock() {

            @Override
            public Object reduce(List<Object> keys, List<Object> values,
                    boolean rereduce) {
                return TDView.totalValues(values);
            }
        }, "1");

        TDStatus updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        List<Map<String,Object>> dumpResult = view.dump();
        Log.v(TAG, "View dump: " + dumpResult);
        Assert.assertEquals(3, dumpResult.size());
        Assert.assertEquals("\"App\"", dumpResult.get(0).get("key"));
        Assert.assertEquals("1.95", dumpResult.get(0).get("value"));
        Assert.assertEquals(2, dumpResult.get(0).get("seq"));
        Assert.assertEquals("\"CD\"", dumpResult.get(1).get("key"));
        Assert.assertEquals("8.99", dumpResult.get(1).get("value"));
        Assert.assertEquals(1, dumpResult.get(1).get("seq"));
        Assert.assertEquals("\"Dessert\"", dumpResult.get(2).get("key"));
        Assert.assertEquals("6.5", dumpResult.get(2).get("value"));
        Assert.assertEquals(3, dumpResult.get(2).get("seq"));

        TDQueryOptions options = new TDQueryOptions();
        options.setReduce(true);
        TDStatus status = new TDStatus();
        List<Map<String, Object>> reduced = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals(1, reduced.size());
        Object value = reduced.get(0).get("value");
        Number numberValue = (Number)value;
        Assert.assertTrue(Math.abs(numberValue.doubleValue() - 17.44) < 0.001);

        db.close();
    }

    public void testViewGrouped() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("_id", "1");
        docProperties1.put("artist", "Gang Of Four");
        docProperties1.put("album", "Entertainment!");
        docProperties1.put("track", "Ether");
        docProperties1.put("time", 231);
        putDoc(db, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("_id", "2");
        docProperties2.put("artist", "Gang Of Four");
        docProperties2.put("album", "Songs Of The Free");
        docProperties2.put("track", "I Love A Man In Uniform");
        docProperties2.put("time", 248);
        putDoc(db, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("_id", "3");
        docProperties3.put("artist", "Gang Of Four");
        docProperties3.put("album", "Entertainment!");
        docProperties3.put("track", "Natural's Not In It");
        docProperties3.put("time", 187);
        putDoc(db, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("_id", "4");
        docProperties4.put("artist", "PiL");
        docProperties4.put("album", "Metal Box");
        docProperties4.put("track", "Memories");
        docProperties4.put("time", 309);
        putDoc(db, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("_id", "5");
        docProperties5.put("artist", "Gang Of Four");
        docProperties5.put("album", "Entertainment!");
        docProperties5.put("track", "Not Great Men");
        docProperties5.put("time", 187);
        putDoc(db, docProperties5);

        TDView view = db.getViewNamed("grouper");
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                List<Object> key = new ArrayList<Object>();
                key.add(document.get("artist"));
                key.add(document.get("album"));
                key.add(document.get("track"));
                emitter.emit(key, document.get("time"));
            }
        }, new TDViewReduceBlock() {

            @Override
            public Object reduce(List<Object> keys, List<Object> values,
                    boolean rereduce) {
                return TDView.totalValues(values);
            }
        }, "1");

        TDStatus status = new TDStatus();
        status = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, status.getCode());

        TDQueryOptions options = new TDQueryOptions();
        options.setReduce(true);
        status = new TDStatus();
        List<Map<String, Object>> rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", null);
        row1.put("value", 1162.0);
        expectedRows.add(row1);

        Assert.assertEquals(expectedRows, rows);

        //now group
        options.setGroup(true);
        status = new TDStatus();
        rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        List<String> key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        key1.add("Ether");
        row1.put("key", key1);
        row1.put("value", 231.0);
        expectedRows.add(row1);

        Map<String,Object> row2 = new HashMap<String,Object>();
        List<String> key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Entertainment!");
        key2.add("Natural's Not In It");
        row2.put("key", key2);
        row2.put("value", 187.0);
        expectedRows.add(row2);

        Map<String,Object> row3 = new HashMap<String,Object>();
        List<String> key3 = new ArrayList<String>();
        key3.add("Gang Of Four");
        key3.add("Entertainment!");
        key3.add("Not Great Men");
        row3.put("key", key3);
        row3.put("value", 187.0);
        expectedRows.add(row3);

        Map<String,Object> row4 = new HashMap<String,Object>();
        List<String> key4 = new ArrayList<String>();
        key4.add("Gang Of Four");
        key4.add("Songs Of The Free");
        key4.add("I Love A Man In Uniform");
        row4.put("key", key4);
        row4.put("value", 248.0);
        expectedRows.add(row4);

        Map<String,Object> row5 = new HashMap<String,Object>();
        List<String> key5 = new ArrayList<String>();
        key5.add("PiL");
        key5.add("Metal Box");
        key5.add("Memories");
        row5.put("key", key5);
        row5.put("value", 309.0);
        expectedRows.add(row5);

        Assert.assertEquals(expectedRows, rows);

        //group level 1
        options.setGroupLevel(1);
        status = new TDStatus();
        rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        row1.put("key", key1);
        row1.put("value", 853.0);
        expectedRows.add(row1);

        row2 = new HashMap<String,Object>();
        key2 = new ArrayList<String>();
        key2.add("PiL");
        row2.put("key", key2);
        row2.put("value", 309.0);
        expectedRows.add(row2);

        Assert.assertEquals(expectedRows, rows);

        //group level 2
        options.setGroupLevel(2);
        status = new TDStatus();
        rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        expectedRows = new ArrayList<Map<String,Object>>();

        row1 = new HashMap<String,Object>();
        key1 = new ArrayList<String>();
        key1.add("Gang Of Four");
        key1.add("Entertainment!");
        row1.put("key", key1);
        row1.put("value", 605.0);
        expectedRows.add(row1);

        row2 = new HashMap<String,Object>();
        key2 = new ArrayList<String>();
        key2.add("Gang Of Four");
        key2.add("Songs Of The Free");
        row2.put("key", key2);
        row2.put("value", 248.0);
        expectedRows.add(row2);

        row3 = new HashMap<String,Object>();
        key3 = new ArrayList<String>();
        key3.add("PiL");
        key3.add("Metal Box");
        row3.put("key", key3);
        row3.put("value", 309.0);
        expectedRows.add(row3);

        Assert.assertEquals(expectedRows, rows);

        db.close();
    }

    public void testViewGroupedStrings() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        Map<String,Object> docProperties1 = new HashMap<String,Object>();
        docProperties1.put("name", "Alice");
        putDoc(db, docProperties1);

        Map<String,Object> docProperties2 = new HashMap<String,Object>();
        docProperties2.put("name", "Albert");
        putDoc(db, docProperties2);

        Map<String,Object> docProperties3 = new HashMap<String,Object>();
        docProperties3.put("name", "Naomi");
        putDoc(db, docProperties3);

        Map<String,Object> docProperties4 = new HashMap<String,Object>();
        docProperties4.put("name", "Jens");
        putDoc(db, docProperties4);

        Map<String,Object> docProperties5 = new HashMap<String,Object>();
        docProperties5.put("name", "Jed");
        putDoc(db, docProperties5);

        TDView view = db.getViewNamed("default/names");
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                String name = (String)document.get("name");
                if(name != null) {
                    emitter.emit(name.substring(0, 1), 1);
                }
            }

        }, new TDViewReduceBlock() {

            @Override
            public Object reduce(List<Object> keys, List<Object> values,
                    boolean rereduce) {
                return values.size();
            }

        }, "1.0");

        TDStatus status = new TDStatus();
        status = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, status.getCode());

        TDQueryOptions options = new TDQueryOptions();
        options.setGroupLevel(1);
        status = new TDStatus();
        List<Map<String,Object>> rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        List<Map<String,Object>> expectedRows = new ArrayList<Map<String,Object>>();
        Map<String,Object> row1 = new HashMap<String,Object>();
        row1.put("key", "A");
        row1.put("value", 2);
        expectedRows.add(row1);
        Map<String,Object> row2 = new HashMap<String,Object>();
        row2.put("key", "J");
        row2.put("value", 2);
        expectedRows.add(row2);
        Map<String,Object> row3 = new HashMap<String,Object>();
        row3.put("key", "N");
        row3.put("value", 1);
        expectedRows.add(row3);

        Assert.assertEquals(expectedRows, rows);

        db.close();
    }

    public void testViewCollation() {
        List<Object> list1 = new ArrayList<Object>();
        list1.add("a");

        List<Object> list2 = new ArrayList<Object>();
        list2.add("b");

        List<Object> list3 = new ArrayList<Object>();
        list3.add("b");
        list3.add("c");

        List<Object> list4 = new ArrayList<Object>();
        list4.add("b");
        list4.add("c");
        list4.add("a");

        List<Object> list5 = new ArrayList<Object>();
        list5.add("b");
        list5.add("d");

        List<Object> list6 = new ArrayList<Object>();
        list6.add("b");
        list6.add("d");
        list6.add("e");


        // Based on CouchDB's "view_collation.js" test
        List<Object> testKeys = new ArrayList<Object>();
        testKeys.add(null);
        testKeys.add(false);
        testKeys.add(true);
        testKeys.add(0);
        testKeys.add(2.5);
        testKeys.add(10);
        testKeys.add(" ");
        testKeys.add("_");
        testKeys.add("~");
        testKeys.add("a");
        testKeys.add("A");
        testKeys.add("aa");
        testKeys.add("b");
        testKeys.add("B");
        testKeys.add("ba");
        testKeys.add("bb");
        testKeys.add(list1);
        testKeys.add(list2);
        testKeys.add(list3);
        testKeys.add(list4);
        testKeys.add(list5);
        testKeys.add(list6);

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        int i=0;
        for (Object key : testKeys) {
            Map<String,Object> docProperties = new HashMap<String,Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(db, docProperties);
        }

        TDView view = db.getViewNamed("default/names");
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        TDQueryOptions options = new TDQueryOptions();
        TDStatus status = new TDStatus();
        List<Map<String,Object>> rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        i = 0;
        for (Map<String, Object> row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.get("key"));
        }

        db.close();
    }


    public void testViewCollationRaw() {
        List<Object> list1 = new ArrayList<Object>();
        list1.add("a");

        List<Object> list2 = new ArrayList<Object>();
        list2.add("b");

        List<Object> list3 = new ArrayList<Object>();
        list3.add("b");
        list3.add("c");

        List<Object> list4 = new ArrayList<Object>();
        list4.add("b");
        list4.add("c");
        list4.add("a");

        List<Object> list5 = new ArrayList<Object>();
        list5.add("b");
        list5.add("d");

        List<Object> list6 = new ArrayList<Object>();
        list6.add("b");
        list6.add("d");
        list6.add("e");


        // Based on CouchDB's "view_collation.js" test
        List<Object> testKeys = new ArrayList<Object>();
        testKeys.add(0);
        testKeys.add(2.5);
        testKeys.add(10);
        testKeys.add(false);
        testKeys.add(null);
        testKeys.add(true);
        testKeys.add(list1);
        testKeys.add(list2);
        testKeys.add(list3);
        testKeys.add(list4);
        testKeys.add(list5);
        testKeys.add(list6);
        testKeys.add(" ");
        testKeys.add("A");
        testKeys.add("B");
        testKeys.add("_");
        testKeys.add("a");
        testKeys.add("aa");
        testKeys.add("b");
        testKeys.add("ba");
        testKeys.add("bb");
        testKeys.add("~");

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        int i=0;
        for (Object key : testKeys) {
            Map<String,Object> docProperties = new HashMap<String,Object>();
            docProperties.put("_id", Integer.toString(i++));
            docProperties.put("name", key);
            putDoc(db, docProperties);
        }

        TDView view = db.getViewNamed("default/names");
        view.setMapReduceBlocks(new TDViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, TDViewMapEmitBlock emitter) {
                emitter.emit(document.get("name"), null);
            }

        }, null, "1.0");

        view.setCollation(TDViewCollation.TDViewCollationRaw);

        TDQueryOptions options = new TDQueryOptions();
        TDStatus status = new TDStatus();
        List<Map<String,Object>> rows = view.queryWithOptions(options, status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        i = 0;
        for (Map<String, Object> row : rows) {
            Assert.assertEquals(testKeys.get(i++), row.get("key"));
        }

        db.close();
    }

    public void testLargerViewQuery() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        putNDocs(db, 4);
        TDView view = createView(db);

        TDStatus updated = view.updateIndex();
        Assert.assertEquals(TDStatus.OK, updated.getCode());

        // Query all rows:
        TDQueryOptions options = new TDQueryOptions();
        TDStatus status = new TDStatus();
        List<Map<String, Object>> rows = view.queryWithOptions(options, status);
    }

}
