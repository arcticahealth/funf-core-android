package edu.mit.media.funf.storage;

import com.google.gson.IJsonObject;

/**
 * Created by astopczynski on 10/27/15.
 */
public interface DatabaseHelper {

    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_VALUE = "value";

    public void init();

    public String getPath();

    public void finish();

    public void insert(String name, double timestamp, IJsonObject value);

}
