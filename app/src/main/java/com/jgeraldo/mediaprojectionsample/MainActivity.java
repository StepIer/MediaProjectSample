package com.jgeraldo.mediaprojectionsample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_MEDIA_PROJECTION_STARTED = "com.jgeraldo.mediaprojectionsample.ACTION_MEDIA_PROJECTION_STARTED";

    public static final String TAG = "MediaProjectionSample";

    private boolean isReceiverRegistered = false;

    private MediaProjectionManager mediaProjectionManager;

    private MediaProjection mMediaProjection;

    private VirtualDisplay mVirtualDisplay;

    private Button mButtonToggle;

    private Surface mSurface;

    private Handler mHandler;

    private ActivityResultLauncher<Intent> startMediaProjectionActivity;

    // Creating a custom BroadcastReceiver class so we can use it externally without needing to declare on the Manifest.
    // The only reason we are using a Broadcast here is to guarantee that we'll only get the MediaProjection instance
    //  when the service has started (otherwise it would throw an exception) and also because we want to show the
    //  shared screen in a SurfaceView hosted on this Activity's (so we couldn't access it from the service directly).
    public class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_MEDIA_PROJECTION_STARTED.equals(intent.getAction())) {
                // Handle the message from the service
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");

                MediaProjectionManager projectionManager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                if (mMediaProjection != null) {
                    // ---------------- STEP 4 ---------------------
                    startScreenCapture();
                }
            }
        }
    }

    private final MyBroadcastReceiver receiver = new MyBroadcastReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceView mSurfaceView = findViewById(R.id.surface);
        mSurface = mSurfaceView.getHolder().getSurface();

        mHandler = new Handler(Looper.getMainLooper());

        mButtonToggle = findViewById(R.id.button);
        mButtonToggle.setOnClickListener(view -> {
            if (mVirtualDisplay == null) {
                requestScreenCapturePermission();
            } else {
                stopScreenCapture();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaProjectionManager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);

        // tracks the createScreenCaptureIntent() result
        startMediaProjectionActivity =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            int resultCode = result.getResultCode();

                            // ---------------- STEP 2 ---------------------
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                Intent data = result.getData();

                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    MediaProjectionManager projectionManager =
                                            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                                    mMediaProjection = projectionManager.getMediaProjection(resultCode, data);

                                    if (mMediaProjection != null) {
                                        // ---------------- STEP 3 (prior Android 14) ---------------------
                                        startScreenCapture();
                                    }
                                } else {
                                    try {
                                        Intent serviceIntent = new Intent(this, MyMediaProjectionService.class);
                                        serviceIntent.putExtra("resultCode", resultCode);
                                        serviceIntent.putExtra("data", data);

                                        // ---------------- STEP 3 (Android 14 and on) ---------------------
                                        ContextCompat.startForegroundService(this, serviceIntent);
                                    } catch (RuntimeException e) {
                                        Log.w(TAG, "Error while trying to get the MediaProjection instance: " + e.getMessage());
                                    }
                                }
                            } else {
                                Toast.makeText(this, "Screen sharing permission denied",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

        if (!isReceiverRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_MEDIA_PROJECTION_STARTED);
            filter.addCategory(Intent.CATEGORY_DEFAULT);

            Log.d(TAG, "REGISTERING RECEIVER <T");
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            isReceiverRegistered = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }

        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void requestScreenCapturePermission() {
        // ---------------- STEP 1 ---------------------
        if (startMediaProjectionActivity != null) {
            Log.d(TAG, "REQUESTING SCREEN CAPTURE INTENT PERMISSION");
            mediaProjectionManager = (MediaProjectionManager)
                    getSystemService(MEDIA_PROJECTION_SERVICE);

            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            Log.d(TAG, "CREATING THE SCREEN CAPTURE INTENT");
            startMediaProjectionActivity.launch(captureIntent);
        }
    }

    public void startScreenCapture() {
        MediaProjection.Callback callback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                // Handle MediaProjection stopped event here
            }
        };

        mMediaProjection.registerCallback(callback, null);

        ImageReader imageReader = ImageReader.newInstance(720, 1080, PixelFormat.RGBA_8888, 2);


        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "ScreenCapture",
                720,
                1080,
                getResources().getDisplayMetrics().densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                imageReader.getSurface(),
                null,
                mHandler);


        mButtonToggle.setText("Stop");

//        virtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
//                width, height, density,
//                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//                imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                saveImage(image);
                image.close();
            }
        }, new Handler(Looper.getMainLooper()));

        // Do whatever you need with the virtualDisplay
    }

    private void saveImage(Image image) {
        Bitmap bitmap = imageToBitmap(image);

        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);

        ImageProcess.INSTANCE.process(recognizer, inputImage);

        File file = new File(getExternalFilesDir(null), "screenshot.png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            Toast.makeText(this, "Screenshot saved!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        Bitmap bitmap = Bitmap.createBitmap(720, 1080, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        mButtonToggle.setText("Start");
    }
}