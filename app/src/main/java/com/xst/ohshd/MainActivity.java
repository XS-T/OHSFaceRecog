package com.xst.ohshd;

import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.view.Surface;

public class MainActivity extends AppCompatActivity {
    private CameraPreview cameraPreview;
    private TextView resultTextView;
    private Camera camera;
    private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreview = findViewById(R.id.cameraPreview);
        resultTextView = findViewById(R.id.resultTextView);

        Button captureButton = findViewById(R.id.captureButton);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureFrame();
            }
        });

        Button switchCameraButton = findViewById(R.id.switchCameraButton);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchCamera();
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

    private void captureFrame() {
        cameraPreview.captureImage(new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                // Get the current device orientation
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

                // Rotate the captured image to match the device orientation
                Bitmap rotatedBitmap = rotateBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), degrees);

                // Convert the rotated bitmap back to byte array
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] rotatedData = byteArrayOutputStream.toByteArray();

                // Send the rotated image to the server
                sendToServer(rotatedData);

                camera.startPreview(); // Restart preview after capturing
            }
        });
    }

    // Helper method to rotate a bitmap
    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void sendToServer(final byte[] imageData) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://10.1.1.34:5000/recognize");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);

                    // Set request headers
                    connection.setRequestProperty("Content-Type", "application/octet-stream");

                    // Write image data to the server
                    OutputStream outputStream = connection.getOutputStream();
                    outputStream.write(imageData);
                    outputStream.flush();
                    outputStream.close();

                    // Get the response from the server
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Process the server response
                        InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                        String result = readStream(inputStream);
                        Log.d("FacialRecognition", "Server response: " + result);

                        // Update UI on the main thread
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resultTextView.setText("Server response: " + result);
                            }
                        });
                    } else {
                        Log.e("FacialRecognition", "Server error, response code: " + responseCode);
                    }

                    connection.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
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
}
