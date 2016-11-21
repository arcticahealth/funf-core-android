/**
 * 
 * Funf: Open Sensing Framework Copyright (C) 2013 Alan Gardner
 * 
 * This file is part of Funf.
 * 
 * Funf is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Funf is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Funf. If not,
 * see <http://www.gnu.org/licenses/>.
 * 
 */
package edu.mit.media.funf.pipeline;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.SQLException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.config.ConfigUpdater;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.config.RuntimeTypeAdapterFactory;
import edu.mit.media.funf.data.Geofencer;
import com.google.gson.IJsonObject;
import edu.mit.media.funf.probe.Probe.DataListener;
import edu.mit.media.funf.probe.builtin.ProbeKeys;
import edu.mit.media.funf.storage.DatabaseHelper;
import edu.mit.media.funf.storage.DefaultArchive;
import edu.mit.media.funf.storage.FileArchive;
import edu.mit.media.funf.storage.JsonDatabaseHelper;
import edu.mit.media.funf.storage.NameValueDatabaseHelper;
import edu.mit.media.funf.storage.RemoteFileArchive;
import edu.mit.media.funf.storage.UploadService;
import edu.mit.media.funf.util.LogUtil;
import edu.mit.media.funf.util.StringUtil;

public class BasicPipeline implements Pipeline, DataListener {

  public static final String 
  ACTION_ARCHIVE = "archive",
  ACTION_UPLOAD = "upload",
  ACTION_UPDATE = "update";
  
  protected final int ARCHIVE = 0, UPLOAD = 1, UPDATE = 2, DATA = 3;
  

  @Configurable
  protected String name = "default";
  
  @Configurable
  protected int version = 1;
  
  @Configurable
  protected FileArchive archive = null;
  
  @Configurable
  protected RemoteFileArchive upload = null;

  @Configurable
  protected ConfigUpdater update = null;

  @Configurable
  protected List<JsonElement> data = new ArrayList<JsonElement>();
  
  @Configurable
  protected Map<String, Schedule> schedules = new HashMap<String, Schedule>();

  @Configurable
  protected Geofencer geofence = new Geofencer();

  @Configurable
  protected boolean broadcastCollectionState = true;

  @Configurable
  protected String file_format = "sqlite"; //can be sqlite or json
  
  private UploadService uploader;

  public static BasicPipeline basicPipeline = null;

  private Integer savingData = -1;

  private boolean enabled;
  private FunfManager manager;
  //private SQLiteOpenHelper databaseHelper = null;
  //private JsonDatabaseHelper databaseHelper = null;
  private DatabaseHelper databaseHelper = null;
  private Looper looper;
  private Handler handler;
  private Handler.Callback callback = new Handler.Callback() {
    
    @Override
    public boolean handleMessage(Message msg) {
      onBeforeRun(msg.what, (JsonObject)msg.obj);
      switch (msg.what) {
        case ARCHIVE:
          if (archive != null) {
            runArchive();
          }
          break;
        case UPLOAD:
          if (archive != null && upload != null && uploader != null) {
            uploader.run(archive, upload);
          }
          break;
        case UPDATE:
          if (update != null) {
            update.run(name, manager);
          }
          break;
        case DATA:
          String name = ((JsonObject)msg.obj).get("name").getAsString();
          IJsonObject data = (IJsonObject)((JsonObject)msg.obj).get("value");
          writeData(name, data);
          break;
        default:
          break;
      }
      onAfterRun(msg.what, (JsonObject)msg.obj);
      return false;
    }
  };
  
  protected void reloadDbHelper(Context ctx) {
    if (this.file_format.equals("json")) {
      this.databaseHelper = new JsonDatabaseHelper(ctx, StringUtil.simpleFilesafe(name), version);
      this.databaseHelper.init();
    } else {
      this.databaseHelper = new NameValueDatabaseHelper(ctx, StringUtil.simpleFilesafe(name), version);
      this.databaseHelper.init();
    }

  }
  
  protected void runArchive() {
    File dbFile = new File(this.databaseHelper.getPath());
    this.databaseHelper.finish();
    if (archive.add(dbFile)) {
      dbFile.delete();
    }
    reloadDbHelper(manager);
  }

  private void broadcastDataCollection() {
    if (!broadcastCollectionState) return;

    if (geofence.shouldSaveData(System.currentTimeMillis())) {
      if (savingData != 1) {
        savingData = 1;
        manager.broadcastDataCollectionStatus(savingData);
      }

    } else {
      if (savingData != 0) {
        savingData = 0;
        manager.broadcastDataCollectionStatus(savingData);
      }
    }
  }

  protected void writeData(String name, IJsonObject data) {

    broadcastDataCollection();
    if (!geofence.shouldSaveData(name, data)) return;

    final double timestamp = data.get(ProbeKeys.BaseProbeKeys.TIMESTAMP).getAsDouble();
    final IJsonObject value = data;
    if (name == null || value == null) {
        Log.e(LogUtil.TAG, "Unable to save data.  Not all required values specified. " + name + " - " + value);
        //TODO custom exception

    }
    if (timestamp == 0L) {
      Log.e(LogUtil.TAG, "Timestamp is null in the probe " + name);
      Log.e(LogUtil.TAG, "This is a funf bug that was causing a crash via runtime exception. I have set it to log this message instead. 11/21/2016 ");
    }

    this.databaseHelper.insert(name, timestamp, value);
  }


  @Override
  public void onCreate(FunfManager manager) {
    basicPipeline = this;
    if (archive == null) {
      archive = new DefaultArchive(manager, name);
    }
    if (uploader == null) {
      uploader = new UploadService(manager);
      uploader.start();
    }
    this.manager = manager;
    reloadDbHelper(manager);
    HandlerThread thread = new HandlerThread(getClass().getName());
    thread.start();
    this.looper = thread.getLooper();
    this.handler = new Handler(looper, callback);
    enabled = true;
    for (JsonElement dataRequest : data) {
      manager.requestData(this, dataRequest);
    }
    for (Map.Entry<String, Schedule> schedule : schedules.entrySet()) {
      manager.registerPipelineAction(this, schedule.getKey(), schedule.getValue());
    }
  }

  @Override
  public void onDestroy() {
    for (JsonElement dataRequest : data) {
      manager.unrequestData(this, dataRequest);
    }
    for (Map.Entry<String, Schedule> schedule : schedules.entrySet()) {
      manager.unregisterPipelineAction(this, schedule.getKey());
    }
    if (uploader != null) {
      uploader.stop();
    }
    looper.quit();
    enabled = false;
  }

  @Override
  public void onRun(String action, JsonElement config) {
    // Run on handler thread
    if (ACTION_ARCHIVE.equals(action) && handler != null) {
      handler.obtainMessage(ARCHIVE, config).sendToTarget();
    } else if (ACTION_UPLOAD.equals(action)) {
      handler.obtainMessage(UPLOAD, config).sendToTarget();
    } else if (ACTION_UPDATE.equals(action)) {
      handler.obtainMessage(UPDATE, config).sendToTarget();
    } 
  }
  
  /**
   * Used as a hook to customize behavior before an action takes place.
   * @param action the type of action taking place
   * @param config the configuration for the action
   */
  protected void onBeforeRun(int action, JsonElement config) {
  }
  
  /**
   * Used as a hook to customize behavior after an action takes place.
   * @param action the type of action taking place
   * @param config the configuration for the action
   */
  protected void onAfterRun(int action, JsonElement config) {
    
  }
  
  protected Handler getHandler() {
    return handler;
  }
  
  protected FunfManager getFunfManager() {
    return manager;
  }
  
  
  //public SQLiteDatabase getDb() {
  //  return databaseHelper.getReadableDatabase();
  //}
  
  public List<JsonElement> getDataRequests() {
    return data == null ? null : Collections.unmodifiableList(data);
  }
  
  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public String getName() {
    return name;
  }


  public void setName(String name) {
    this.name = name;
  }


  public int getVersion() {
    return version;
  }


  public void setVersion(int version) {
    this.version = version;
  }


  public FileArchive getArchive() {
    return archive;
  }


  public void setArchive(FileArchive archive) {
    this.archive = archive;
  }


  public RemoteFileArchive getUpload() {
    return upload;
  }


  public void setUpload(RemoteFileArchive upload) {
    this.upload = upload;
  }


  public ConfigUpdater getUpdate() {
    return update;
  }


  public void setUpdate(ConfigUpdater update) {
    this.update = update;
  }


  public void setDataRequests(List<JsonElement> data) {
    this.data = new ArrayList<JsonElement>(data); // Defensive copy
  }


  public Map<String, Schedule> getSchedules() {
    return schedules;
  }


  public void setSchedules(Map<String, Schedule> schedules) {
    this.schedules = schedules;
  }


  public UploadService getUploader() {
    return uploader;
  }


  public void setUploader(UploadService uploader) {
    this.uploader = uploader;
  }


  //public SQLiteOpenHelper getDatabaseHelper() {
  //  return databaseHelper;
  //}


  //public void setDatabaseHelper(SQLiteOpenHelper databaseHelper) {
  //  this.databaseHelper = databaseHelper;
  //}


  @Override
  public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {
    JsonObject record = new JsonObject();
    record.add("name", probeConfig.get(RuntimeTypeAdapterFactory.TYPE));
    record.add("value", data);
    handler.obtainMessage(DATA, record).sendToTarget();
  }

  @Override
  public void onDataCompleted(IJsonObject probeConfig, JsonElement checkpoint) {
    // TODO Figure out what to do with continuations of probes, if anything

  }

  public List<JsonElement> getFences() {
    return this.geofence.getFences();
  }

  public Geofencer getGeofence() {
    return this.geofence;
  }
}
