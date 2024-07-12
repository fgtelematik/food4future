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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;

import java.util.List;

/** For f4f modified version of Google's Barcode Detector Demo. */
public class QRScannerProcessor extends VisionProcessorBase<List<Barcode>> {

  public interface QRCodeDetectedListener {
    void onSingleQRCodeDetected(Barcode qrCode);
  }

  private static final String TAG = "BarcodeProcessor";

  private final BarcodeScanner barcodeScanner;
  private QRCodeDetectedListener qrCodeDetectedListener = null;

  public QRScannerProcessor(Context context, boolean barcodeMode) {
    super(context);

    int barcodeFormats;
    if(barcodeMode) {
      barcodeFormats = Barcode.FORMAT_EAN_8 |Barcode.FORMAT_EAN_13 |Barcode.FORMAT_UPC_A | Barcode.FORMAT_UPC_E;
    } else {
      barcodeFormats = Barcode.FORMAT_QR_CODE;
    }

    // Note that if you know which format of barcode your app is dealing with, detection will be
    // faster to specify the supported barcode formats one by one, e.g.
    BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
        .setBarcodeFormats(barcodeFormats)
        .build();
    barcodeScanner = BarcodeScanning.getClient(options);
  }

  @Override
  public void stop() {
    super.stop();
    barcodeScanner.close();
  }

  @Override
  protected Task<List<Barcode>> detectInImage(InputImage image) {
    return barcodeScanner.process(image);
  }

  @Override
  protected void onSuccess(
      @NonNull List<Barcode> barcodes, @NonNull GraphicOverlay graphicOverlay) {
    for (int i = 0; i < barcodes.size(); ++i) {
      Barcode barcode = barcodes.get(i);
      graphicOverlay.add(new QRCodeGraphicOverlay(graphicOverlay, barcode));
    }

    if(barcodes.size() == 1 && qrCodeDetectedListener != null) {
      // only notify when one single QR code is detected in image:
      qrCodeDetectedListener.onSingleQRCodeDetected(barcodes.get(0));
    }
  }

  public void setQrCodeDetectedListener(QRCodeDetectedListener qrCodeDetectedListener) {
    this.qrCodeDetectedListener = qrCodeDetectedListener;
  }

  @Override
  protected void onFailure(@NonNull Exception e) {
    Log.e(TAG, "Barcode detection failed " + e);
  }
}
