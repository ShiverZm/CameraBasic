/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.camera2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.example.camerax.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Camera2Activity extends AppCompatActivity {

    //Define variables for button and textureView
    ImageButton button;
    TextureView textureView;

    /* Define a SparseIntArray. This is used to hold the orientation of the camera and
        convert from screen rotation to JPEG orientation. */

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    //Append orientations to the SparseIntArray.
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }


    private String cameraId; //Holds ID of the camera device

    CameraDevice cameraDevice;
    CameraCaptureSession CCS;
    CaptureRequest captureRequest;
    CaptureRequest.Builder CRB;

    private Size imgDim; //hold dimensions of the image.
    private ImageReader imageReader;
    private File file;
    Handler mBackgroundHandler; //Run tasks in the background
    HandlerThread mBackgroundThread; //Thread for running tasks that shouldn't block the UI.


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        //Find views
        textureView = findViewById(R.id.textureView);
        button = findViewById(R.id.captureButton);
        textureView.setSurfaceTextureListener(textureListener);

        //Save image when button clicked.
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    takePicture();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });


    }

    //Check if permissions are allowed for the camera.

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 101){
            if(grantResults[0] == PackageManager.PERMISSION_DENIED){
                Toast.makeText(getApplicationContext(),"Cannot Start Camera Permission Denied!",Toast.LENGTH_LONG).show();
                //finish();

            }
        }
    }

    //Create new TextureView.SurfaceTextureListener.
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {

        //Invoked when a TextureView's SurfaceTexture is ready for use.

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

            try {
                openCamera(); //Call open camera function.
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    //A callback object for receiving updates about the state of a camera device.
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            try {
                createCameraPreview(); //When the camera is opened start the preview.
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            cameraDevice.close(); //Close camera if there is an error opening it.
            cameraDevice = null;
        }
    };

    private void createCameraPreview() throws CameraAccessException {
        //Display preview on texture view.
        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(imgDim.getWidth(),imgDim.getHeight());
        Surface surface = new Surface(texture);

        //Create a CaptureRequest.Builder for new capture requests, initialized with template for a target use case.
        CRB = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CRB.addTarget(surface); //Add camera request builder to surface.
        //Create a new camera capture session by providing the target output set of Surfaces to the camera device.
        cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            //This method is called when the camera device has finished configuring itself, and the session can start processing capture requests.
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                if(cameraDevice == null){
                    return;
                }

                CCS = cameraCaptureSession;
                try {
                    updatePreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                Toast.makeText(getApplicationContext(),"Configuration Changed",Toast.LENGTH_LONG).show();
            }
        },null);
    }

    private void updatePreview() throws CameraAccessException {
        if(cameraDevice == null){
            return;
        }

        CRB.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        CCS.setRepeatingRequest(CRB.build(),null,mBackgroundHandler);
    }

    //Open the camera.
    private void openCamera() throws CameraAccessException {

        //Connect to system camera.
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        cameraId = manager.getCameraIdList()[0]; //0 = rear camera.

        CameraCharacteristics characteristics = manager.getCameraCharacteristics((cameraId));

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        imgDim = map.getOutputSizes(SurfaceTexture.class)[0]; //get image dimensions.

        //Check if required permissions are available.
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(Camera2Activity.this,new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE},101);
            return;
        }

        manager.openCamera(cameraId,stateCallback,null); //Open a connection to a camera with the given ID.

    }

    //Take a picture when button pressed.
    private void takePicture() throws CameraAccessException {
        if(cameraDevice == null){
            return;
        }

        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId()); //Properties describing a CameraDevice.

        Size[] jpegSizes = null;

        jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);

        int width = 640;
        int height = 480;

        if(jpegSizes!=null && jpegSizes.length>0){
            width = jpegSizes [0].getWidth();
            height = jpegSizes [0].getHeight();
        }

        //Render image data onto surface.
        final ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
        List<Surface> outputSurfaces = new ArrayList<>(2);
        outputSurfaces.add(reader.getSurface());

        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

        final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(reader.getSurface());
        captureBuilder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));

        //Get system time.
        Long tsLong = System.currentTimeMillis()/1000;
        String ts = tsLong.toString();

        //Create new file.
        file = new File(("/sdcard" + "/" +ts+".jpg"));


        Toast.makeText(this,file.getAbsolutePath(),Toast.LENGTH_SHORT).show();

        //Callback interface for being notified that a new image is available.
        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = null;

                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                try {
                    save(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    if(image!=null){
                        image.close();
                    }
                }


            }
        };

        //Register a listener to be invoked when a new image becomes available from the ImageReader.
        reader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

        //When capture complete.
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                Toast.makeText(getApplicationContext(),"Image Saved!",Toast.LENGTH_LONG).show();
                try {
                    createCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        };

        cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                try {
                    //Submit a request for an image to be captured by the camera device.
                    cameraCaptureSession.capture(captureBuilder.build(),captureListener,mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

            }
        },mBackgroundHandler);

    }

    //Save output steam into file.
    private void save(byte[] bytes) throws IOException {
        OutputStream outputStream = null;
        outputStream = new FileOutputStream(file);

        outputStream.write(bytes);

        outputStream.close();
    }

    //Resume background thread.
    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if(textureView.isAvailable()){
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        else {
            textureView.setSurfaceTextureListener(textureListener);
        }

    }

    //Start background thread.
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    protected void onPause() {
        try {
            stopBackgroundThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onPause();
    }
    //Stop background thread safely.
    protected void stopBackgroundThread() throws InterruptedException {
        mBackgroundThread.quitSafely();
        mBackgroundThread.join();
        mBackgroundThread=null;
        mBackgroundHandler=null;

    }
}