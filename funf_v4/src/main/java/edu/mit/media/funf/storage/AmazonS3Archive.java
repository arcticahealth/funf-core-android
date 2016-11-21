package edu.mit.media.funf.storage;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import edu.mit.media.funf.BuildConfig;
import edu.mit.media.funf.config.Configurable;

public class AmazonS3Archive implements RemoteFileArchive {

    @Configurable
    private static String cognitoPoolID = BuildConfig.COGNITO_POOL_ID;
    @Configurable
    private static String bucketName = BuildConfig.S3_BUCKET_NAME;
    @Configurable
    private static Regions region=Regions.US_WEST_2;
    @Configurable
    private String url;
    @Configurable
    private boolean wifiOnly = false;

    private Context context;

    @SuppressWarnings("unused")
    private String mimeType;

    private static AmazonS3Client s3Client;
    private static CognitoCachingCredentialsProvider sCredProvider;
    private static TransferUtility sTransferUtility;

    public AmazonS3Archive(){}
    @Override
    public boolean add(File file) {

        assert context != null;

        Future<Boolean> future = startUpload(file);
        Boolean result = false;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return result;
    }

    private Future<Boolean> startUpload(File file) {
        if (sTransferUtility == null) {
            sTransferUtility = new TransferUtility(getS3Client(context.getApplicationContext()), context.getApplicationContext());
        }
        final SettableFuture<Boolean> future = SettableFuture.create() ;
        TelephonyManager tMgr = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        String mPhoneNumber = tMgr.getDeviceId();
        final String key = "behavior/" + mPhoneNumber + "/" + file.getName();
        TransferObserver observer = sTransferUtility.upload(bucketName, key,
                file);
        observer.setTransferListener(new TransferListener() {
            @Override
            public void onStateChanged(int id, TransferState state) {
                if (state == TransferState.COMPLETED) {
                    future.set(true);
                } else if (state == TransferState.FAILED) {
                    future.set(false);
                }
            }

            @Override
            public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

            }

            @Override
            public void onError(int id, Exception ex) {

            }
        });
        return future;
    }


    private AmazonS3 getS3Client(Context context) {
        if (s3Client == null) {
            s3Client = new AmazonS3Client(getCredProvider(context.getApplicationContext()));
        }
        return s3Client;
    }

    private static CognitoCachingCredentialsProvider getCredProvider(Context context) {
        if (sCredProvider == null) {
            sCredProvider = new CognitoCachingCredentialsProvider(
                    context.getApplicationContext(),
                    cognitoPoolID,
                    region);
        }

        return sCredProvider;
    }

    public AmazonS3Archive(Context context, final String uploadUrl) {
        this(context, uploadUrl, "application/x-binary");
    }

    public AmazonS3Archive(Context context, final String uploadUrl, final String mimeType) {
        this.url = uploadUrl;
        this.mimeType = mimeType;
    }

    @Override
    public boolean isAvailable() {
        assert context != null;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        if (!wifiOnly && netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        } else if (wifiOnly) {
            NetworkInfo.State wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
            if (NetworkInfo.State.CONNECTED.equals(wifiInfo) || NetworkInfo.State.CONNECTING.equals(wifiInfo)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public String getId() {
        return url;
    }
}
