package com.theta360.sample.v2;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.theta360.sample.v2.glview.GLPhotoView;
import com.theta360.sample.v2.model.Photo;
import com.theta360.sample.v2.model.RotateInertia;
import com.theta360.sample.v2.network.HttpDownloadListener;
import com.theta360.sample.v2.network.HttpConnector;
import com.theta360.sample.v2.network.ImageData;
import com.theta360.sample.v2.view.ConfigurationDialog;
import com.theta360.sample.v2.view.LogView;

import java.io.ByteArrayInputStream;


/**
 * Activity that displays photo object as a sphere
 */
public class GLPhotoActivity extends Activity implements ConfigurationDialog.DialogBtnListener {

    private static final String CAMERA_IP_ADDRESS = "CAMERA_IP_ADDRESS";
    private static final String OBJECT_ID = "OBJECT_ID";
    private static final String THUMBNAIL = "THUMBNAIL";

    private GLPhotoView mGLPhotoView;

    private Photo mTexture = null;
    private LoadPhotoTask mLoadPhotoTask = null;

    private RotateInertia mRotateInertia = RotateInertia.INERTIA_0;

    public static final int REQUEST_REFRESH_LIST = 100;
    public static final int REQUEST_NOT_REFRESH_LIST = 101;

    /**
     * onCreate Method
     * @param savedInstanceState onCreate Status value
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_glphoto);

        Intent intent = getIntent();
        String cameraIpAddress = intent.getStringExtra(CAMERA_IP_ADDRESS);
        String fileId = intent.getStringExtra(OBJECT_ID);
        byte[] byteThumbnail = intent.getByteArrayExtra(THUMBNAIL);

        ByteArrayInputStream inputStreamThumbnail = new ByteArrayInputStream(byteThumbnail);
        Drawable thumbnail = BitmapDrawable.createFromStream(inputStreamThumbnail, null);

        Photo _thumbnail = new Photo(((BitmapDrawable)thumbnail).getBitmap());

        mGLPhotoView = (GLPhotoView) findViewById(R.id.photo_image);
        mGLPhotoView.setTexture(_thumbnail);
        mGLPhotoView.setmRotateInertia(mRotateInertia);

        mLoadPhotoTask = new LoadPhotoTask(cameraIpAddress, fileId);
        mLoadPhotoTask.execute();
    }

    @Override
    protected void onDestroy() {
        if (mTexture != null) {
            mTexture.getPhoto().recycle();
        }
        if (mLoadPhotoTask != null) {
            mLoadPhotoTask.cancel(true);
        }
        super.onDestroy();
    }

    /**
     * onCreateOptionsMenu method
     * @param menu Menu initialization object
     * @return Menu display feasibility status value
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.configuration, menu);

        return true;
    }


    /**
     * onOptionsItemSelected Method
     * @param item Process menu
     * @return Menu process continuation feasibility value
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.configuration:
                FragmentManager mgr = getFragmentManager();
                ConfigurationDialog.show(mgr, mRotateInertia);
                break;
            default:
                break;
        }
        return true;
    }


    /**
     * onResume Method
     */
    @Override
    protected void onResume() {
        super.onResume();
        mGLPhotoView.onResume();

        if (null != mTexture) {
            if (null != mGLPhotoView) {
                mGLPhotoView.setTexture(mTexture);
            }
        }
    }

    /**
     * onPause Method
     */
    @Override
    protected void onPause() {
        this.mGLPhotoView.onPause();
        super.onPause();
    }


    /**
     * onDialogCommitClick Method
     * @param inertia selected inertia
     */
    @Override
    public void onDialogCommitClick(RotateInertia inertia) {
        mRotateInertia = inertia;
        if (null != mGLPhotoView) {
            mGLPhotoView.setmRotateInertia(mRotateInertia);
        }
    }


    private class LoadPhotoTask extends AsyncTask<Void, Object, ImageData> {

        private LogView logViewer;
        private ProgressBar progressBar;
        private String cameraIpAddress;
        private String fileId;
        private long fileSize;
        private long receivedDataSize = 0;

        public LoadPhotoTask(String cameraIpAddress, String fileId) {
            this.logViewer = (LogView) findViewById(R.id.photo_info);
            this.progressBar = (ProgressBar) findViewById(R.id.loading_photo_progress_bar);
            this.cameraIpAddress = cameraIpAddress;
            this.fileId = fileId;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected ImageData doInBackground(Void... params) {
            try {
                publishProgress("start to download image" + fileId);
                HttpConnector camera = new HttpConnector(cameraIpAddress);
                ImageData resizedImageData = camera.getImage(fileId, new HttpDownloadListener() {
                    @Override
                    public void onTotalSize(long totalSize) {
                        fileSize = totalSize;
                    }

                    @Override
                    public void onDataReceived(int size) {
                        receivedDataSize += size;

                        if (fileSize != 0) {
                            int progressPercentage = (int) (receivedDataSize * 100 / fileSize);
                            publishProgress(progressPercentage);
                        }
                    }
                });
                publishProgress("finish to download");

                return resizedImageData;

            } catch (Throwable throwable) {
                String errorLog = Log.getStackTraceString(throwable);
                publishProgress(errorLog);
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            for (Object param : values) {
                if (param instanceof Integer) {
                    progressBar.setProgress((Integer) param);
                } else if (param instanceof String) {
                    logViewer.append((String) param);
                }
            }
        }

        @Override
        protected void onPostExecute(ImageData imageData) {
            if (imageData != null) {

                byte[] dataObject = imageData.getRawData();

                if (dataObject == null) {
                    logViewer.append("failed to download image");
                    return;
                }

                Bitmap __bitmap = BitmapFactory.decodeByteArray(dataObject, 0, dataObject.length);

                progressBar.setVisibility(View.GONE);

                Double yaw = imageData.getYaw();
                Double pitch = imageData.getPitch();
                Double roll = imageData.getRoll();
                logViewer.append("<Angle: yaw=" + yaw + ", pitch=" + pitch + ", roll=" + roll + ">");

                mTexture = new Photo(__bitmap, yaw, pitch, roll);
                if (null != mGLPhotoView) {
                    mGLPhotoView.setTexture(mTexture);
                }
            } else {
                logViewer.append("failed to download image");
            }
        }
    }

    /**
     * Activity call method
     * 
     * @param activity Call source activity
     * @param cameraIpAddress IP address for camera device
     * @param fileId Photo object identifier
     * @param thumbnail Thumbnail
     * @param refreshAfterClose true is to refresh list after closing this activity, otherwise is not to refresh
     */
    public static void startActivityForResult(Activity activity, String cameraIpAddress, String fileId, byte[] thumbnail, boolean refreshAfterClose) {
        int requestCode;
        if (refreshAfterClose) {
            requestCode = REQUEST_REFRESH_LIST;
        } else {
            requestCode = REQUEST_NOT_REFRESH_LIST;
        }

        Intent intent = new Intent(activity, GLPhotoActivity.class);
        intent.putExtra(CAMERA_IP_ADDRESS, cameraIpAddress);
        intent.putExtra(OBJECT_ID, fileId);
        intent.putExtra(THUMBNAIL, thumbnail);
        activity.startActivityForResult(intent, requestCode);
    }
}