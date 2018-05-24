package com.android.baina.silentcamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

/**
 * Created by taochen on 18-5-21.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_EXTERNAL_STORAGE = 0x001;
    private static final int REQUEST_CAMERA = 0x002;

    private ImageView mDataIv;
    private Camera mCamera;
    private static HandlerThread sWorkerThread = new HandlerThread("take picture");
    private Handler mWorker = new Handler(sWorkerThread.getLooper());

    static {
        sWorkerThread.start();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDataIv = (ImageView) findViewById(R.id.dataIv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
            surfaceView.setVisibility(View.VISIBLE);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            surfaceHolder.addCallback(new NewSurfaceHolder());
        } else {
            requestPermission();
        }
    }

    private void requestPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Please open related permissions, otherwise you can not use this application normally! ", Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);

        } else {
            Log.d(TAG, "requestPermission: WRITE_EXTERNAL_STORAGE PERMISSION_GRANTED！");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Please open related permissions, otherwise you can not use this application normally! ", Toast.LENGTH_SHORT).show();
            }
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);

        } else {
            Log.d(TAG, "requestPermission: CAMERA PERMISSION_GRANTED！");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && requestCode == REQUEST_EXTERNAL_STORAGE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "requestPermission: WRITE_EXTERNAL_STORAGE PERMISSION_GRANTED！");
        }
        if (grantResults.length > 0 && requestCode == REQUEST_CAMERA && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "requestPermission: CAMERA PERMISSION_GRANTED！");
        }
    }


    class NewSurfaceHolder implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            mWorker.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        int numberOfCameras = Camera.getNumberOfCameras();
                        for (int i = 0; i < numberOfCameras; i++) {
                            Camera.getCameraInfo(i, cameraInfo);
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                releaseCameraAndPreview();
                                mCamera = Camera.open(i);

                                setCameraParameters();

                                mCamera.setPreviewDisplay(holder);
                                mCamera.setDisplayOrientation(Utils.getPreviewDegree(MainActivity.this));
                                mCamera.startPreview();
                                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                                    @Override
                                    public void onAutoFocus(boolean success, Camera camera) {
                                        mCamera.takePicture(null, null, new Camera.PictureCallback() {
                                            @Override
                                            public void onPictureTaken(byte[] data, Camera camera) {
                                                Bitmap source = BitmapFactory.decodeByteArray(data, 0, data.length);
                                                int degree = Utils.readPictureDegree(getFilePath());
                                                final Bitmap bitmap = Utils.rotateImageView(degree, source);
                                                Utils.saveBitmap(bitmap, new File(getFilePath()));
                                                mDataIv.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mDataIv.setImageBitmap(bitmap);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            releaseCameraAndPreview();
        }
    }

    private void setCameraParameters() {
        Camera.Parameters parameters = mCamera.getParameters(); // 获取各项参数
        parameters.setPictureFormat(PixelFormat.JPEG); // 设置图片格式
        parameters.setJpegQuality(100); // 设置照片质量
        int mPreviewHeight = parameters.getPreviewSize().height;
        int mPreviewWidth = parameters.getPreviewSize().width;
        parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
        parameters.setPictureSize(mPreviewWidth, mPreviewHeight);
        mCamera.setParameters(parameters);
    }

    private void releaseCameraAndPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private String getFilePath() {
        boolean canCreateOutside = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || Environment.isExternalStorageRemovable();
        if (canCreateOutside) {
            File filesExternalDir = getExternalFilesDir(null);
            if (filesExternalDir != null) {
                return filesExternalDir.getPath() + "/" + System.currentTimeMillis() + ".jpg";
            }
        }
        return getFilesDir().getPath() + "/" + System.currentTimeMillis() + ".jpg";
    }
}
