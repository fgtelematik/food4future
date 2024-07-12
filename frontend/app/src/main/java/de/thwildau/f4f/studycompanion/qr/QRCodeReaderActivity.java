/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.thwildau.f4f.studycompanion.qr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory;

import com.google.android.gms.common.annotation.KeepName;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.demo.CameraXViewModel;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.VisionImageProcessor;

import java.util.ArrayList;
import java.util.List;

import de.thwildau.f4f.studycompanion.R;

/**
 * This source code is based on Google's demo app for ML Kit APIs using CameraX,
 * simplified and modified for use as pure QR code reader within f4f Study Companion.
 */
@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
public final class QRCodeReaderActivity extends AppCompatActivity
        implements OnRequestPermissionsResultCallback, QRScannerProcessor.QRCodeDetectedListener {
    private static final String LOG_TAG = "BarcodeReaderActivity";

    public static final String EXTRA_BARCODE_DATA = "EXTRA_BARCODE_DATA";
    public static final String EXTRA_BARCODE_FORMAT = "EXTRA_BARCODE_FORMAT";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_INFOTEXT = "EXTRA_INFOTEXT";
    public static final String EXTRA_BARCODE_MODE = "EXTRA_BARCODE_MODE";

    private static final int PERMISSION_REQUESTS = 1;
    private static final String BARCODE_SCANNING = "Barcode Scanning";

    private static final String STATE_SELECTED_MODEL = "selected_model";
    private static final String STATE_LENS_FACING = "lens_facing";

    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;

    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private Preview previewUseCase;
    @Nullable
    private ImageAnalysis analysisUseCase;
    @Nullable
    private VisionImageProcessor imageProcessor;
    private boolean needUpdateGraphicOverlayImageSourceInfo;

    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private CameraSelector cameraSelector;
    private boolean barcodeMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String helpText = getIntent().getStringExtra(EXTRA_INFOTEXT);
        barcodeMode = getIntent().getBooleanExtra(EXTRA_BARCODE_MODE, false);

        getSupportActionBar().setTitle(title != null ? title : getString(R.string.qrcode_reader_default_title));

        if (savedInstanceState != null) {
            lensFacing = savedInstanceState.getInt(STATE_LENS_FACING, CameraSelector.LENS_FACING_BACK);
        }
        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        setContentView(R.layout.activity_qr_reader);

        TextView textHelpText = findViewById(R.id.textQrReaderHelpText);
        if (helpText == null) {
            textHelpText.setVisibility(View.GONE);
        } else {
            textHelpText.setVisibility(View.VISIBLE);
            textHelpText.setText(helpText);
        }

        previewView = findViewById(R.id.preview_view);
        if (previewView == null) {
            Log.d(LOG_TAG, "previewView is null");
        }
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if (graphicOverlay == null) {
            Log.d(LOG_TAG, "graphicOverlay is null");
        }


        new ViewModelProvider(this, AndroidViewModelFactory.getInstance(getApplication()))
                .get(CameraXViewModel.class)
                .getProcessCameraProvider()
                .observe(
                        this,
                        provider -> {
                            cameraProvider = provider;
                            bindAllCameraUseCases();
                        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(STATE_LENS_FACING, lensFacing);
    }


    public void changeFacing() {
        Log.d(LOG_TAG, "Set facing");
        if (cameraProvider == null) {
            return;
        }

        int newLensFacing =
                lensFacing == CameraSelector.LENS_FACING_FRONT
                        ? CameraSelector.LENS_FACING_BACK
                        : CameraSelector.LENS_FACING_FRONT;
        CameraSelector newCameraSelector =
                new CameraSelector.Builder().requireLensFacing(newLensFacing).build();
        try {
            if (cameraProvider.hasCamera(newCameraSelector)) {
                lensFacing = newLensFacing;
                cameraSelector = newCameraSelector;
                bindAllCameraUseCases();
                return;
            }
        } catch (CameraInfoUnavailableException e) {
            // Falls through
        }
        Toast.makeText(
                getApplicationContext(),
                "This device does not have lens with facing: " + newLensFacing,
                Toast.LENGTH_SHORT)
                .show();
    }


    @Override
    public void onResume() {
        super.onResume();
        bindAllCameraUseCases();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }

    private void bindAllCameraUseCases() {
        if (cameraProvider != null) {
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider.unbindAll();
            bindPreviewUseCase();
            bindAnalysisUseCase();
        }
    }

    private void bindPreviewUseCase() {
//    if (!PreferenceUtils.isCameraLiveViewportEnabled(this)) {
//      return;
//    }
        if (cameraProvider == null) {
            return;
        }
        if (previewUseCase != null) {
            cameraProvider.unbind(previewUseCase);
        }

        Preview.Builder builder = new Preview.Builder();
//    Size targetResolution = PreferenceUtils.getCameraXTargetResolution(this);
        Size targetResolution = null;
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution);
        }
        previewUseCase = builder.build();
        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, previewUseCase);
    }

    private void bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return;
        }
        if (analysisUseCase != null) {
            cameraProvider.unbind(analysisUseCase);
        }
        if (imageProcessor != null) {
            imageProcessor.stop();
        }

        try {

            Log.i(LOG_TAG, "Using Barcode Detector Processor");
            imageProcessor = new QRScannerProcessor(this, barcodeMode);

            ((QRScannerProcessor) imageProcessor).setQrCodeDetectedListener(this);

        } catch (Exception e) {
            Log.e(LOG_TAG, "Can not create barcode scanner image processor.", e);
            Toast.makeText(
                    getApplicationContext(),
                    "Can not create image processor: " + e.getLocalizedMessage(),
                    Toast.LENGTH_LONG)
                    .show();
            return;
        }

        ImageAnalysis.Builder builder = new ImageAnalysis.Builder();
//    Size targetResolution = PreferenceUtils.getCameraXTargetResolution(this);
//    if (targetResolution != null) {
//      builder.setTargetResolution(targetResolution);
//    }
        analysisUseCase = builder.build();

        needUpdateGraphicOverlayImageSourceInfo = true;
        analysisUseCase.setAnalyzer(
                // imageProcessor.processImageProxy will use another thread to run the detection underneath,
                // thus we can just runs the analyzer itself on main thread.
                ContextCompat.getMainExecutor(this),
                imageProxy -> {
                    if (needUpdateGraphicOverlayImageSourceInfo) {
                        boolean isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT;
                        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                        if (rotationDegrees == 0 || rotationDegrees == 180) {
                            graphicOverlay.setImageSourceInfo(
                                    imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped);
                        } else {
                            graphicOverlay.setImageSourceInfo(
                                    imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped);
                        }
                        needUpdateGraphicOverlayImageSourceInfo = false;
                    }
                    try {
                        imageProcessor.processImageProxy(imageProxy, graphicOverlay);
                    } catch (MlKitException e) {
                        Log.e(LOG_TAG, "Failed to process image. Error: " + e.getLocalizedMessage());
                        Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        cameraProvider.bindToLifecycle(/* lifecycleOwner= */ this, cameraSelector, analysisUseCase);
    }

    @Override
    public void onSingleQRCodeDetected(Barcode qrCode) {
        Intent i = new Intent();
        i.putExtra(EXTRA_BARCODE_DATA, qrCode.getRawValue());
        i.putExtra(EXTRA_BARCODE_FORMAT, convertFormat(qrCode.getFormat()));
        setResult(Activity.RESULT_OK, i);
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
        finish();
    }

    private static String convertFormat(int format) {
        String res = "";
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                res = "QR";
                break;
            case Barcode.FORMAT_EAN_8:
                res = "EAN-8";
                break;
            case Barcode.FORMAT_EAN_13:
                res = "EAN-13";
                break;
            case Barcode.FORMAT_UPC_A:
                res = "UPC-A";
                break;
            case Barcode.FORMAT_UPC_E:
                res = "UPC-E";
                break;
        }
        return res;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(Activity.RESULT_CANCELED);
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
        finish();
    }
}
