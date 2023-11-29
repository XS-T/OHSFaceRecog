package com.xst.ohshd;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.URL;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.content.pm.ActivityInfo;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private CameraPreview cameraPreview;
    private TextView resultTextView;
    private Camera camera;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private MediaPlayer whore_sound;

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            } else {
                // CAMERA permission granted, initialize the camera
                initializeCamera();
            }
        } else {
            // For devices below Android M, no need to request permissions, initialize the camera
            initializeCamera();
        }


        cameraPreview = findViewById(R.id.cameraPreview);
        resultTextView = findViewById(R.id.resultTextView);

        // Initialize the MediaPlayer
        whore_sound = MediaPlayer.create(this, R.raw.whore_alert);

        Button captureButton = findViewById(R.id.captureButton);
        Button uploadFaceButton = findViewById(R.id.uploadFaceButton);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureFrame(captureButton, uploadFaceButton);
                captureButton.setVisibility(View.INVISIBLE);
                uploadFaceButton.setVisibility(View.INVISIBLE);
            }
        });

        Button switchCameraButton = findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchCamera();
            }
        });

        uploadFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadFace(uploadFaceButton, captureButton);
                uploadFaceButton.setVisibility(View.INVISIBLE);
                captureButton.setVisibility(View.INVISIBLE);
            }
        });

        initializeCamera();
    }

    private void initializeCamera() {
        try {
            releaseCamera();
            camera = Camera.open(currentCameraId);
            camera.setDisplayOrientation(90); // Set the display orientation
            cameraPreview.setCamera(camera);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    private void captureFrame(Button captureButton, Button uploadButton) {
        cameraPreview.captureImage(new Camera.PictureCallback() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int degrees = 0;

                switch (rotation) {
                    case Surface.ROTATION_0:
                        degrees = 90;
                        break;
                    case Surface.ROTATION_90:
                        degrees = 0;
                        break;
                    case Surface.ROTATION_180:
                        degrees = 270;
                        break;
                    case Surface.ROTATION_270:
                        degrees = 180;
                        break;
                }

                Bitmap rotatedBitmap = rotateBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), degrees);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] rotatedData = byteArrayOutputStream.toByteArray();

                sendToServer(rotatedData, captureButton, uploadButton);
                resultTextView.setText("Sending to Server");
                camera.startPreview();
            }
        });
    }

    private void uploadFace(Button uploadButton, Button capButton) {
        cameraPreview.captureImage(new Camera.PictureCallback() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int degrees = 0;

                switch (rotation) {
                    case Surface.ROTATION_0:
                        degrees = 90;
                        break;
                    case Surface.ROTATION_90:
                        degrees = 0;
                        break;
                    case Surface.ROTATION_180:
                        degrees = 270;
                        break;
                    case Surface.ROTATION_270:
                        degrees = 180;
                        break;
                }

                Bitmap rotatedBitmap = rotateBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), degrees);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] rotatedData = byteArrayOutputStream.toByteArray();

                uploadToServer(rotatedData, capButton, uploadButton);
                resultTextView.setText("Uploading Face...");
                camera.startPreview();
            }
        });
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void sendToServer(final byte[] imageData, Button captureButton, Button uploadButton) {
        new ServerTask("http://ohshd.aris-net.com/recognize", imageData, captureButton, uploadButton, whore_sound).execute();
    }

    private void uploadToServer(final byte[] imageData, Button captureButton, Button uploadButton) {
        new ServerTask("http://ohshd.aris-net.com/upload", imageData, captureButton, uploadButton, whore_sound).execute();
    }

    private String readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

    private void switchCamera() {
        releaseCamera();
        currentCameraId = (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
                ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK;
        initializeCamera();
    }

    @SuppressLint("StaticFieldLeak")
    private class ServerTask extends AsyncTask<Void, Void, String> {
        private final String url;
        private final byte[] imageData;
        private final Button captureButton;
        private final Button uploadButton;
        private final MediaPlayer mediaPlayer;

        public ServerTask(String url, byte[] imageData, Button captureButton, Button uploadButton, MediaPlayer mediaPlayer) {
            this.url = url;
            this.imageData = imageData;
            this.captureButton = captureButton;
            this.uploadButton = uploadButton;
            this.mediaPlayer = mediaPlayer;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL(this.url);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

                connection.setRequestProperty("Content-Type", "application/octet-stream");

                try {
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(imageData);
                    outputStream.flush();
                    outputStream.close();
                } catch (NoRouteToHostException e) {
                    Log.e("FacialRecognition", "NoRouteToHostException: Server is not online");
                    return "NoRouteToHostException: Server is not online";
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                    return readStream(inputStream);
                } else {
                    Log.e("FacialRecognition", "Server error, response code: " + responseCode);
                    return "Server error, response code: " + responseCode;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {
                    resultTextView.setText("Server response: " + result);
                    if (result != null && result.contains("Detected") && !mediaPlayer.isPlaying()) {
                        mediaPlayer.start();
                    }
                    captureButton.setVisibility(View.VISIBLE);
                    uploadButton.setVisibility(View.VISIBLE);
                }
            });
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // CAMERA permission granted, restart the app
                restartApp();
            } else {
                // Permission denied, handle accordingly (e.g., show a message or close the app)
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void restartApp() {
        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }
}
