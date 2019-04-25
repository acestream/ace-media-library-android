package org.videolan.vlc;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.BaseService;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.util.Constants;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ProcessTransportFilesService extends Service implements Handler.Callback {
    public static final String TAG = "AS/PTFS";

    private static final int MSG_JOB_STARTED = 0;
    private static final int MSG_JOB_FINISHED = 1;
    private static final int MSG_NOTIFY_ML_UPDATED = 2;
    private static final int MSG_NOTIFY_PROGRESS = 3;

    private static final int MIN_ORPHAN_TRANSPORT_FILE_AGE = 3600000;

    private static volatile boolean sJobIsRunning = false;
    private volatile boolean mShutdownFlag = false;

    private LocalBroadcastManager mLocalBroadcastManager;
    private AceStreamManager.Client mAceStreamManagerClient;
    private AceStreamManager mAceStreamManager;
    private EngineApi mEngineService;
    private Handler mHandler = new Handler(this);

    // broadcast receiver
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent == null) return;
            if(TextUtils.equals(intent.getAction(), AceStream.ACTION_STOP_APP)) {
                Log.d(TAG, "receiver: stop app: class=" + ProcessTransportFilesService.this.getClass().getSimpleName());
                shutdown();
            }
        }
    };

    private AceStreamManager.Client.Callback mAceStreamManagerClientCallback = new AceStreamManager.Client.Callback() {
        @Override
        public void onConnected(AceStreamManager service) {
            Logger.v(TAG, "pm connected");
            mAceStreamManager = service;
            mAceStreamManager.getEngine(false, new IAceStreamManager.EngineStateCallback() {
                @Override
                public void onEngineConnected(@NonNull IAceStreamManager playbackManager, @NonNull EngineApi engineApi) {
                    if(mEngineService == null) {
                        Logger.v(TAG, "engine connected");
                        mEngineService = engineApi;
                        VLCApplication.runBackground(new Runnable() {
                            @Override
                            public void run() {
                                doJob();
                            }
                        });
                    }
                }
            });
        }

        @Override
        public void onDisconnected() {
            Logger.v(TAG, "pm disconnected");
            mAceStreamManager = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mAceStreamManagerClient = new AceStreamManager.Client(this, mAceStreamManagerClientCallback);
        mAceStreamManagerClient.connect(false);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(AceStream.ACTION_STOP_APP);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");

        mAceStreamManagerClient.disconnect();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null) {
            Log.e(TAG, "onHandleIntent: null intent");
            return START_NOT_STICKY;
        }

        Logger.v(TAG, "onHandleIntent: action=" + intent.getAction());

        // No need to lock sJobIsRunning flag because this is always called on main thread
        if(sJobIsRunning) {
            Log.v(TAG, "onHandleIntent: job is already running");
            return START_NOT_STICKY;
        }

        sJobIsRunning = true;
        mShutdownFlag = false;
        startJob();

        return START_NOT_STICKY;
    }

    private void startJob() {
        if(mEngineService != null) {
            Logger.v(TAG, "startJob: start now");
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    doJob();
                }
            });
        }
        else {
            Logger.v(TAG, "startJob: wait engine");
        }
        Logger.v(TAG, "startJob: done");
    }

    private void jobStarted() {
        mHandler.sendEmptyMessage(MSG_JOB_STARTED);
    }

    private void jobFinished() {
        mHandler.sendEmptyMessage(MSG_JOB_FINISHED);
    }

    private void doJob() {
        jobStarted();

        if(mEngineService == null) {
            Logger.v(TAG, "doJob: missing engine");
            jobFinished();
            return;
        }

        final Medialibrary ml = Medialibrary.getInstance();
        if(!ml.isInitiated()) {
            jobFinished();
            return;
        }

        int seq = 0;
        MediaWrapper[] tfiles = ml.getUnparsedTransportFiles();
        for(final MediaWrapper mw: tfiles) {
            mHandler.obtainMessage(MSG_NOTIFY_PROGRESS, seq, tfiles.length)
                    .sendToTarget();
            ++seq;
            Logger.v(TAG, "processTransportFiles: seq=" + seq + "/" + tfiles.length + " id=" + mw.getId() + " parsed=" + mw.isParsed() + " uri=" + mw.getUri());

            final CountDownLatch countDown = new CountDownLatch(1);
            final Object sync = new Object();
            try {
                // Pass decoded uri to TFD to avoid double encoding.
                final Uri decodedUri = Uri.parse(Uri.decode(mw.getUri().toString()));
                final File transportFile = MiscUtils.getFile(decodedUri);
                final TransportFileDescriptor descriptor = TransportFileDescriptor.fromContentUri(this.getContentResolver(), decodedUri);
                mEngineService.getMediaFiles(descriptor, new org.acestream.engine.controller.Callback<MediaFilesResponse>() {
                    @Override
                    public void onSuccess(final MediaFilesResponse result) {
                        // This callback is called on main thread, but we need to process
                        // in background.
                        VLCApplication.runBackground(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    boolean isMulti = result.files.length > 1;
                                    for (MediaFilesResponse.MediaFile mf : result.files) {
                                        MediaWrapper item = ml.addP2PMedia(mw.getId(), descriptor, mf);
                                        if (isMulti && item != null && !TextUtils.isEmpty(result.name)) {
                                            // Set group name for multifile torrent
                                            item.setStringMeta(MediaWrapper.META_GROUP_NAME, result.name);
                                        }
                                        if (item != null) {
                                            if (!mf.isLive()) {
                                                item.setLongMeta(MediaWrapper.META_FILE_SIZE,
                                                        mf.size);
                                            }
                                            item.setStringMeta(MediaWrapper.META_TRANSPORT_FILE_PATH,
                                                    decodedUri.getPath());
                                            if (transportFile != null) {
                                                item.setLongMeta(MediaWrapper.META_LAST_MODIFIED,
                                                        transportFile.lastModified());
                                            }
                                        }
                                        if (Logger.verbose()) {
                                            if (item == null)
                                                Logger.v(TAG, "processTransportFiles: failed to add p2p item");
                                            else
                                                Logger.v(TAG, "processTransportFiles: p2p item added:"
                                                        + " id=" + item.getId()
                                                        + " parent=" + item.getParentMediaId()
                                                        + " p2p=" + item.isP2PItem()
                                                        + " type=" + item.getType()
                                                        + " title=" + item.getTitle()
                                                        + " mrl=" + item.getUri()
                                                        + " isMulti=" + isMulti
                                                        + " groupName=" + result.name
                                                        + " lastModified=" + item.getLastModified()
                                                );
                                        }
                                    }

                                    mw.setParsed(true);
                                    synchronized(sync) {
                                        sync.notifyAll();
                                    }
                                }
                                finally {
                                    countDown.countDown();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String err) {
                        countDown.countDown();
                        Log.e(TAG, "processTransportFiles: failed to process transport file: uri=" + mw.getUri() + " err=" + err);
                    }
                });
            }
            catch(IOException e) {
                countDown.countDown();
                Log.e(TAG, "processTransportFiles: failed to open transport file: uri=" + mw.getUri(), e);
            }

            synchronized(sync) {
                while (countDown.getCount() > 0) {
                    Logger.v(TAG, "processTransportFiles: wait sync...");
                    try {
                        sync.wait(1000);
                        Logger.v(TAG, "processTransportFiles: wait sync done: countDown=" + countDown.getCount());
                    } catch (InterruptedException e) {
                        Logger.v(TAG, "processTransportFiles: sync interrupted");
                        break;
                    }
                }
            }

            Logger.v(TAG, "processTransportFiles: wait countdown...");
            try {
                countDown.await(10, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
                Logger.v(TAG, "processTransportFiles: countdown wait interrupted");
            }
            Logger.v(TAG, "processTransportFiles: wait countdown done");

            mHandler.obtainMessage(MSG_NOTIFY_PROGRESS, seq, tfiles.length)
                    .sendToTarget();

            if(mShutdownFlag) {
                Log.d(TAG, "processTransportFiles: got shutdown flag");
                break;
            }
        }

        Logger.v(TAG, "processTransportFiles: " + tfiles.length + " files processed");

        boolean somethingDeleted = checkDuplicates(ml);
        cleanupInternalTransportFiles();

        if(somethingDeleted || tfiles.length > 0) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_ML_UPDATED);
        }
        jobFinished();
    }

    private boolean checkDuplicates(Medialibrary ml) {
        boolean somethingDeleted = false;
        MediaWrapper[] duplicates = ml.findDuplicatesByInfohash();
        Map<String,MediaWrapper> keys = new HashMap<>(duplicates.length);
        for(MediaWrapper item: duplicates) {
            Logger.debugAssert(item.isP2PItem(), TAG, "checkDuplicates: not p2p item");
            Logger.debugAssert(item.getInfohash() != null, TAG, "checkDuplicates: null infohash");
            String key = item.getInfohash() + ":" + item.getFileIndex();

            if(keys.containsKey(key)) {
                MediaWrapper item2 = keys.get(key);
                boolean internal1;
                boolean internal2;

                try {
                    internal1 = item.getDescriptor().isInternal();
                }
                catch(TransportFileParsingException e) {
                    if(e.getMissingFilePath() != null) {
                        Log.e(TAG, "checkDuplicates: missing file, delete media: id=" + item.getId() + " path=" + e.getMissingFilePath());
                        ml.deleteMedia(item.getId());
                    }
                    else {
                        Log.e(TAG, "checkDuplicates: failed to parse transport file", e);
                    }
                    continue;
                }

                try {
                    internal2 = item2.getDescriptor().isInternal();
                }
                catch(TransportFileParsingException e) {
                    if(e.getMissingFilePath() != null) {
                        Log.e(TAG, "checkDuplicates: missing file, delete media: id=" + item2.getId() + " path=" + e.getMissingFilePath());
                        ml.deleteMedia(item2.getId());
                    }
                    else {
                        Log.e(TAG, "checkDuplicates: failed to parse transport file", e);
                    }
                    continue;
                }

                Logger.v(TAG, "checkDuplicates: got duplicate: key=" + key + " a=" + item + " b=" + item2);
                if(internal2 && !internal1) {
                    // Stored item is internal and new item is not.
                    // Replace stored item with new.
                    Logger.v(TAG, "checkDuplicates: delete and copy metadata: media=" + item2 + " copy=" + item2.getId() + "->" + item.getId());
                    ml.copyMetadata(item2.getId(), item.getId());
                    ml.deleteMedia(item2.getId());
                    keys.put(key, item);
                    somethingDeleted = true;
                }
                else {
                    // Discard new item
                    Logger.v(TAG, "checkDuplicates: delete and copy metadata: media=" + item + " copy=" + item.getId() + "->" + item2.getId());
                    ml.copyMetadata(item.getId(), item2.getId());
                    ml.deleteMedia(item.getId());
                    somethingDeleted = true;
                }
            }
            else {
                keys.put(key, item);
            }
        }

        return somethingDeleted;
    }

    private void cleanupInternalTransportFiles() {
        final Medialibrary ml = Medialibrary.getInstance();
        if(!ml.isInitiated())
            return;

        File dir = AceStream.getTransportFilesDir(true);
        File[] files = dir.listFiles();
        if(files == null)
            return;

        Map<String,File> filesMap = new HashMap<>(files.length);
        for (File f : files) {
            long age = System.currentTimeMillis() - f.lastModified();
            if(age > MIN_ORPHAN_TRANSPORT_FILE_AGE) {
                filesMap.put(f.getAbsolutePath(), f);
            }
        }

        MediaWrapper[] list = ml.findMediaByParent(Medialibrary.INTERNAL_TRANSPORT_FILE_PARENT_ID);
        for(MediaWrapper item: list) {
            try {
                TransportFileDescriptor descriptor = item.getDescriptor();
                if(descriptor != null) {
                    filesMap.remove(descriptor.getLocalPath());
                }
            }
            catch(TransportFileParsingException e) {
                if(e.getMissingFilePath() != null) {
                    Log.e(TAG, "cleanupInternalTransportFiles: missing file, delete media: id=" + item.getId() + " path=" + e.getMissingFilePath());
                    ml.deleteMedia(item.getId());
                }
                else {
                    Log.e(TAG, "cleanupInternalTransportFiles: failed to get descriptor", e);
                }
            }
        }

        for(Map.Entry<String, File> item: filesMap.entrySet()) {
            Logger.v(TAG, "cleanupInternalTransportFiles: delete orphan file: " + item.getValue().getAbsolutePath());
            if(!item.getValue().delete()) {
                Logger.v(TAG, "cleanupInternalTransportFiles: failed to delete orphan file: " + item.getValue().getAbsolutePath());
            }
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case MSG_NOTIFY_ML_UPDATED:
                Logger.v(TAG, "notify ML updated");
                mLocalBroadcastManager.sendBroadcast(new Intent(Constants.ACTION_MEDIALIBRARY_UPDATED));
                return true;

            case MSG_NOTIFY_PROGRESS:
                StringBuilder sb = new StringBuilder();
                int percent = 0;
                if(message.arg2 != 0) {
                    percent = Math.round(message.arg1 / (float) message.arg2 * 100);
                }
                sb.append(getResources().getString(R.string.parsing_transport_files));
                sb.append(" ").append(percent).append("%");
                mLocalBroadcastManager.sendBroadcast(
                        new Intent(Constants.ACTION_PTF_PROGRESS)
                                .putExtra(Constants.ACTION_PROGRESS_TEXT, sb.toString())
                                .putExtra(Constants.ACTION_PROGRESS_VALUE, percent));
                return true;

            case MSG_JOB_STARTED:
                Logger.v(TAG, "job started");
                mLocalBroadcastManager.sendBroadcast(new Intent(Constants.ACTION_PTF_SERVICE_STARTED));
                return true;

            case MSG_JOB_FINISHED:
                Logger.v(TAG, "job finished");
                mLocalBroadcastManager.sendBroadcast(new Intent(Constants.ACTION_PTF_SERVICE_ENDED));
                sJobIsRunning = false;
                stopSelf();
                return true;
        }
        return false;
    }

    private void shutdown() {
        Log.d(TAG, "shutdown");
        stopSelf();
        mShutdownFlag = true;
    }
}
