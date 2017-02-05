package edu.mit.media.funf.storage;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import edu.mit.media.funf.BuildConfigHelper;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.util.IOUtil;

public class AmazonS3Archive implements RemoteFileArchive {

    public static final String TAG = AmazonS3Archive.class.getSimpleName();
    @Configurable
    private static String bucketName = BuildConfigHelper.S3_BUCKET_NAME;
    @Configurable
    private String url;
    @Configurable
    private boolean wifiOnly = false;

    private Context context;

    private String mimeType;

    public interface CloudLoggingUtility {
        void log(String message);
        void log(Exception e);

        void log(String message, Exception e);
    }

    public interface CredentialsProviderProvider {
        CognitoCredentialsProvider getCredentialProvider();
    }
    public AmazonS3Archive(){}


    private void log(String message) {
        Log.e(TAG, message);
        if (context.getApplicationContext() instanceof CloudLoggingUtility) {
            CloudLoggingUtility cloudLoggingUtility = (CloudLoggingUtility) context.getApplicationContext();
            cloudLoggingUtility.log(message);
        }
    }
    private void log(Exception e) {
        e.printStackTrace();
        if (context.getApplicationContext() instanceof CloudLoggingUtility) {
            CloudLoggingUtility cloudLoggingUtility = (CloudLoggingUtility) context.getApplicationContext();
            cloudLoggingUtility.log(e);
        }
    }
    private void log(String message, Exception e) {
        Log.e(TAG,message);
        e.printStackTrace();
        if (context.getApplicationContext() instanceof CloudLoggingUtility) {
            CloudLoggingUtility cloudLoggingUtility = (CloudLoggingUtility) context.getApplicationContext();
            cloudLoggingUtility.log(message,e);
        }
    }

    @Override
    public boolean add(File file) {

        if (context == null) {
            log("Context is null, can't get cred provider");
            return false;
        }

        final CredentialsProviderProvider credentialsProviderProvider =
                (CredentialsProviderProvider) context.getApplicationContext();
        CognitoCredentialsProvider credentialsProvider =
                credentialsProviderProvider.getCredentialProvider();

        if (credentialsProvider == null) {
            log("credentialProvider is null");
            return false;
        }
        logCredentialStatus(credentialsProvider);
        // It's actually pretty helpful to have this turned on... 
        //if (BuildConfigHelper.DEBUG) {
        //    return true;
        //}
        Future<Boolean> future = startUpload(file,credentialsProvider);
        if (future == null) {
            return false;
        }
        Boolean result = false;
        try {
            result = future.get();
        } catch (InterruptedException e) {
            log(e);
        } catch (ExecutionException e) {
            log(e);
        }
        if (!result) {
            TelephonyManager tMgr = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            log("User: " + tMgr.getDeviceId() + " " + " unable to upload file: " +file.getName());
        }
        return result;
    }

    private Future<Boolean> startUpload(File file, CognitoCredentialsProvider credentialsProvider) {

        final AmazonS3 s3Client = new AmazonS3Client(credentialsProvider);
        TransferUtility transferUtility = new TransferUtility(s3Client, context.getApplicationContext());

        // Tell S3 to use server side encryption using the provided key
        ObjectMetadata myObjectMetadata = new ObjectMetadata();
        myObjectMetadata.setSSEKMSKeyId(BuildConfigHelper.S3_KMS_KEY);
        myObjectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        // We'll need the phone number
        TelephonyManager tMgr = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        String mPhoneNumber = tMgr.getDeviceId();

        // Set our upload url for s3
        String fileUrl;
        try {
            fileUrl = credentialsProvider.getIdentityId() + "/" + mPhoneNumber + "/" + file.getName();
        } catch (NotAuthorizedException e){
            log("Not authorized user: " + mPhoneNumber, e);
            return null;
        }

        // Set the future... becase we are magicians
        final SettableFuture<Boolean> future = SettableFuture.create() ;

        TransferObserver observer = transferUtility.upload(bucketName, fileUrl, file, myObjectMetadata);
        observer.setTransferListener(new S3TransferListener(fileUrl, future));
        return future;
    }
    private static boolean logCredentialStatus(CognitoCredentialsProvider credentialsProvider) {

        final Date credentialsExpirationDate = credentialsProvider.getSessionCredentitalsExpiration();

        if (credentialsExpirationDate == null) {
            Log.e(TAG, "Credentials are EXPIRED (null expiration).");
            return true;
        }

        long currentTime = System.currentTimeMillis() -
                (long)(SDKGlobalConfiguration.getGlobalTimeOffset() * 1000);

        final long expireTime = credentialsExpirationDate.getTime();
        final boolean credsAreExpired =
                (expireTime - currentTime) < 0;

        Log.d(TAG, "Credentials expire at: " + new Date(expireTime));
        Log.d(TAG, "Credentials are " + (credsAreExpired ? "EXPIRED." : "OK"));

        return credsAreExpired;
    }

    public AmazonS3Archive(Context context, final String uploadUrl) {
        this(context, uploadUrl, "application/x-binary");
    }

    private AmazonS3Archive(Context context, final String uploadUrl, final String mimeType) {
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

    private static class S3TransferListener implements TransferListener {
        private final String fileUrl;
        private final SettableFuture<Boolean> future;

        S3TransferListener(String key, SettableFuture<Boolean> future) {
            this.fileUrl = key;
            this.future = future;
        }

        @Override
        public void onStateChanged(int id, TransferState state) {
            if (state == TransferState.COMPLETED) {
                Log.d(TAG,"upload completed for: " + bucketName + "/" + fileUrl);
                future.set(true);
            } else if (state == TransferState.FAILED) {
                Log.e(TAG,"upload FAILED for: " + bucketName + "/" + fileUrl);
                future.set(false);
            }
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

        }

        @Override
        public void onError(int id, Exception ex) {

        }
    }
}
