package com.kewldevs.sathish.filepicker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.chooser.android.DbxChooser;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.OpenFileActivityBuilder;
import com.microsoft.onedrivesdk.picker.IPicker;
import com.microsoft.onedrivesdk.picker.IPickerResult;
import com.microsoft.onedrivesdk.picker.LinkType;
import com.microsoft.onedrivesdk.picker.Picker;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;

/*
TODO:
1.Add Dependency
2.Add Manifest Entry for Dropbox Auth
3.Change the Secret and App Key


*/
public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "FilePicker";
    private TextView tv;
    private ProgressBar mProgressBar;
    private Button bt;

    //GoogleDrive
    GoogleApiClient mGoogleApiClient;
    private static final int REQUEST_CODE_RESOLUTION = 3;
    private static final int REQUEST_CODE_OPENER = 2;


    //Dropbox KEY's
    private static final String APP_KEY = "";  //Paste your app key
    private static final String APP_SECRET = ""; //Paste your app secret key

    private static final String ACCOUNT_PREFS_NAME = "prefs";
    private static final String ACCESS_KEY_NAME = "ACCESS_KEY";
    private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    static final int DBX_CHOOSER_REQUEST = 1;
    private static final boolean USE_OAUTH1 = false;

    static final int ACCESS_STORAGE_CODE = 5;

    private DbxChooser mChooser;


    DropboxAPI<AndroidAuthSession> mApi;

    //One Drive KEY
    private static final String ONEDRIVE_APP_KEY = ""; //Paste your app key
    private IPicker mPicker;

    boolean mDropboxLoggedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();

        AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<AndroidAuthSession>(session);
        checkAppKeySetup();
        mChooser = new DbxChooser(APP_KEY);

        mPicker = Picker.createPicker(ONEDRIVE_APP_KEY);

        tv = (TextView) findViewById(R.id.tvName);
        //setMessage("Not Connected");
        bt = (Button) findViewById(R.id.btOpen);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);
        mProgressBar.setMax(100);

    }

    public void clicked(View view) {

        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission is granted");
                buildDialog();

            } else {

                Log.v(TAG, "Permission is revoked");
                toast("Grand permission to access storage!");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ACCESS_STORAGE_CODE);

            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG, "Permission is granted");
            buildDialog();

        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission: " + permissions[0] + "was " + grantResults[0]);
            //resume tasks needing this permission
            buildDialog();
        }
    }

    private void buildDialog() {

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle("Select");

        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.pop_up, null);
        alertDialogBuilder.setView(dialogView);
        LinearLayout dropBox = (LinearLayout) dialogView.findViewById(R.id.drop_box);
        LinearLayout googleDrive = (LinearLayout) dialogView.findViewById(R.id.google_drive);
        LinearLayout oneDrive = (LinearLayout) dialogView.findViewById(R.id.one_drive);
        final AlertDialog alertDialog = alertDialogBuilder.create();

        dropBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: DropBox Invoked");
                alertDialog.dismiss();
                invokeDropboxApi();

            }
        });

        googleDrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: GoogleDrive Invoked");
                try {
                    alertDialog.dismiss();
                    invokeGoogleDriveApi();
                } catch (IllegalStateException e) {
                    toast(e.getMessage());
                }

            }
        });

        oneDrive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: OneDrive Invoked");
                alertDialog.dismiss();
                invokeOneDriveApi();
            }
        });


        alertDialog.show();

    }

    private void invokeOneDriveApi() {

        mPicker.startPicking(MainActivity.this, LinkType.DownloadLink);

    }

    private void invokeGoogleDriveApi() {
        //Getting Auth

        try {
            //Opening Chooser
            //
            /*

     "xls" =>'application/vnd.ms-excel',
    "xlsx" =>'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    "xml" =>'text/xml',
    "ods"=>'application/vnd.oasis.opendocument.spreadsheet',
    "csv"=>'text/plain',
    "tmpl"=>'text/plain',
    "pdf"=> 'application/pdf',
    "php"=>'application/x-httpd-php',
    "jpg"=>'image/jpeg',
    "png"=>'image/png',
    "gif"=>'image/gif',
    "bmp"=>'image/bmp',
    "txt"=>'text/plain',
    "doc"=>'application/msword',
    "js"=>'text/js',
    "swf"=>'application/x-shockwave-flash',
    "mp3"=>'audio/mpeg',
    "zip"=>'application/zip',
    "rar"=>'application/rar',
    "tar"=>'application/tar',
    "arj"=>'application/arj',
    "cab"=>'application/cab',
    "html"=>'text/html',
    "htm"=>'text/html',
    "default"=>'application/octet-stream',
    "folder"=>'application/vnd.google-apps.folder'

    */
            String[] mimeTypes = new String[]{
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/xml",
                    "application/vnd.oasis.opendocument.spreadsheet",
                    "application/vnd.oasis.opendocument.text",
                    "application/vnd.oasis.opendocument.presentation",
                    "text/plain",
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "image/gif",
                    "image/bmp",
                    "application/msword",
                    "application/zip",
                    "application/rar",
                    "application/vnd.google-apps.folder",
                    "application/vnd.google-apps.document",
                    "application/vnd.google-apps.file",
                    "application/vnd.google-apps.presentation",
                    "application/vnd.google-apps.spreadsheet"
            };
            IntentSender intentSender = Drive.DriveApi.newOpenFileActivityBuilder().setMimeType(mimeTypes)
                    .build(mGoogleApiClient);

            startIntentSenderForResult(
                    intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.w(TAG, "Unable to send intent", e);
        }

    }

    private void invokeDropboxApi() {


        if (mDropboxLoggedIn) {
            toast("Connect to Dropbox");
        } else {
            // Start the remote authentication
            if (USE_OAUTH1) {
                mApi.getSession().startAuthentication(MainActivity.this);
            } else {
                mApi.getSession().startAuthentication(MainActivity.this);

            }
            toast(mApi.getSession().isLinked() ? "Connected to Dropbox" : "Failed to connect Dropbox");
        }

        mChooser.forResultType(DbxChooser.ResultType.FILE_CONTENT)
                .launch(MainActivity.this, DBX_CHOOSER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        IPickerResult oneresult = mPicker.getPickerResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OPENER && resultCode == RESULT_OK) {
            //Google Drive Result
            DriveId selectedFileDriveId = (DriveId) data.getParcelableExtra(
                    OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
            Log.d(TAG, "onActivityResult: " + selectedFileDriveId.getResourceId());
            downloadFromGoogleDrive(selectedFileDriveId);

        } else if (requestCode == DBX_CHOOSER_REQUEST && resultCode == RESULT_OK) {
            //Dropbox Result
            DbxChooser.Result result = new DbxChooser.Result(data);
            String link = null;
            try {
                link = URLDecoder.decode(result.getLink().toString(), "UTF-8");
                link = link.substring(6, link.length());
                Log.d(TAG, "Download loc: " + link);
                downloadDropboxFile(link, result.getName());
            } catch (UnsupportedEncodingException e) {
                toast("Unsupported Link");
            }

        } else if (oneresult != null) {
            //OneDrive Result
            Log.d("main", "Link to file '" + oneresult.getName() + ": " + oneresult.getLink());
            downloadFromOneDrive(oneresult.getLink(), oneresult.getName());
        } else {

            super.onActivityResult(requestCode, resultCode, data);
        }


    }

    private void downloadFromOneDrive(final Uri link, final String fileName) {

        mProgressBar.setProgress(0);
        File root = android.os.Environment.getExternalStorageDirectory();

        File dir = new File(root.getPath() + "/filepicker");
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        final File file = new File(dir, fileName);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                int count;
                try {
                    URL url = new URL(link.toString());
                    URLConnection conection = url.openConnection();
                    conection.connect();
                    // getting file length
                    final int lenghtOfFile = conection.getContentLength();

                    // input stream to read file - with 8k buffer
                    InputStream input = new BufferedInputStream(url.openStream(), 8192);

                    // Output stream to write file
                    OutputStream output = new FileOutputStream(file);

                    byte data[] = new byte[1024];

                    long total = 0;

                    while ((count = input.read(data)) != -1) {
                        total += count;
                        // publishing the progress....
                        // After this onProgressUpdate will be called

                        final long finalTotal = total;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                int progress = (int) ((finalTotal * 100) / lenghtOfFile);
                                mProgressBar.setProgress(progress);
                                setMessage(String.format("Downloading progress: %d percent", progress));
                            }
                        });


                        // writing data to file
                        output.write(data, 0, count);
                    }

                    // flushing output
                    output.flush();

                    // closing streams
                    output.close();
                    input.close();


                } catch (Exception e) {
                    Log.e("Error: ", e.getMessage());
                }


                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                setMessage("Downloaded at " + file.getPath());
                toast("Downloaded from OneDrive");
            }
        }.execute();


    }

    private void downloadDropboxFile(final String mPath, String name) {

        mProgressBar.setProgress(0);
        File root = android.os.Environment.getExternalStorageDirectory();

        File dir = new File(root.getPath() + "/filepicker");
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        final File file = new File(dir, name);
        new AsyncTask<Void, Void, Void>() {


            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    File f = new File(mPath);
                    if (f.exists()) {
                        f.renameTo(file);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgressBar.setProgress(100);
                                setMessage("Downloaded at " + file.getPath());
                                toast("Downloaded from Dropbox");
                            }
                        });

                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();


    }

    private void downloadFromGoogleDrive(DriveId selectedFileDriveId) {
        // Reset progress dialog back to zero as we're
        // initiating an opening request.
        Log.d(TAG, "downloadFromGoogleDrive: ");
        setMessage("Downloading from Drive");
        mProgressBar.setProgress(0);
        final DriveFile driveFile = selectedFileDriveId.asDriveFile();

        DriveFile.DownloadProgressListener listener = new DriveFile.DownloadProgressListener() {
            @Override
            public void onProgress(long bytesDownloaded, long bytesExpected) {
                // Update progress dialog with the latest progress.
                int progress = (int) (bytesDownloaded * 100 / bytesExpected);
                setMessage(String.format("Downloading progress: %d percent", progress));
                mProgressBar.setProgress(progress);
            }
        };


        driveFile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, listener)
                .setResultCallback(driveContentsCallback);

    }


    ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback = new ResultCallback<DriveApi.DriveContentsResult>() {
        @Override
        public void onResult(@NonNull final DriveApi.DriveContentsResult result) {
            if (!result.getStatus().isSuccess()) {
                // display an error saying file can't be opened
                toast("This file can't be downloaded");
                Log.d(TAG, "OnResult: " + result.getStatus());
                return;
            }


            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... voids) {

                    Log.d(TAG, "doInBackground: Downloading");
                    String fileName = null;
                    DriveContents driveContents = result.getDriveContents();

                    DriveFile f = Drive.DriveApi.getFile(mGoogleApiClient, driveContents.getDriveId());
                    DriveResource.MetadataResult mdRslt = f.getMetadata(mGoogleApiClient).await();
                    if (mdRslt != null && mdRslt.getStatus().isSuccess()) {
                        Log.d(TAG, "fileName:" + mdRslt.getMetadata().getOriginalFilename());
                        fileName = mdRslt.getMetadata().getOriginalFilename();
                    }

                    InputStream inputstream = driveContents.getInputStream();
                    File root = Environment.getExternalStorageDirectory();

                    File dir = new File(root.getAbsolutePath() + "/filepicker");
                    if (dir.exists() == false) {
                        dir.mkdirs();
                    }
                    Log.d("DownloadManager", "DownloadSaveLocation: " + dir.toString());

                    if (fileName != null) {
                        final File file = new File(dir, fileName);

                        try {
                            FileOutputStream fileOutput = new FileOutputStream(file);

                            byte[] buffer = new byte[1024];
                            int bufferLength = 0;
                            while ((bufferLength = inputstream.read(buffer)) > 0) {
                                fileOutput.write(buffer, 0, bufferLength);
                            }
                            fileOutput.close();
                            inputstream.close();
                            Log.d(TAG, "doInBackground: File Downloaded");

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setMessage("Downloaded at " + file.getPath());
                                    toast("Downloaded from Drive!");

                                }
                            });

                        } catch (IOException e) {
                            // TODO Auto-generated catch block

                            e.printStackTrace();
                        }
                    }


                    return null;
                }
            }.execute();

        }
    };


    @Override
    protected void onResume() {
        super.onResume();

        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }


        AndroidAuthSession session = mApi.getSession();
        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();
                // Store it locally in our app for later use
                storeAuth(session);

            } catch (IllegalStateException e) {
                toast("Couldn't authenticate with Dropbox:" + e.getLocalizedMessage());
                Log.i(TAG, "Error authenticating", e);
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "API client connected.");
        setMessage("Connected to Google Drive");
        toast("Connected to Google Drive");
    }


    @Override
    public void onConnectionSuspended(int i) {
        setMessage("Google Drive Connection Suspended");
        Log.i(TAG, "GoogleApiClient connection suspended");

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {

        setMessage("Google Drive Not Connected");

        if (!result.hasResolution()) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            return;
        }
        try {

            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {

            Log.e(TAG, "Exception while starting resolution activity", e);
        }

    }

    public void setMessage(String x) {
        tv.setText(x);
    }

    public void toast(String x) {
        Toast.makeText(this, x, Toast.LENGTH_SHORT).show();
    }

    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair, Session.AccessType.DROPBOX);
        loadAuth(session);
        return session;
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is for OAuth 2.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        } else {
            // Still support using old OAuth 1 tokens.
            session.setAccessTokenPair(new AccessTokenPair(key, secret));
        }
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        AccessTokenPair oauth2AccessToken = session.getAccessTokenPair();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken.secret);
            edit.commit();
            return;
        }
        // Store the OAuth 1 access token, if there is one.  This is only necessary if
        // you're still using OAuth 1.
        AccessTokenPair oauth1AccessToken = session.getAccessTokenPair();
        if (oauth1AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, oauth1AccessToken.key);
            edit.putString(ACCESS_SECRET_NAME, oauth1AccessToken.secret);
            edit.commit();
            return;
        }
    }

    private void clearKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    private void checkAppKeySetup() {
        // Check to make sure that we have a valid app key
        if (APP_KEY.startsWith("CHANGE") ||
                APP_SECRET.startsWith("CHANGE")) {
            toast("No Key");
            finish();
            return;
        }

        // Check if the app has set up its manifest properly.
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        String scheme = "db-" + APP_KEY;
        String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
        testIntent.setData(Uri.parse(uri));
        PackageManager pm = getPackageManager();
        if (0 == pm.queryIntentActivities(testIntent, 0).size()) {
            toast("URL scheme in your app's " +
                    "manifest is not set up correctly");
            finish();
        }
    }

}
