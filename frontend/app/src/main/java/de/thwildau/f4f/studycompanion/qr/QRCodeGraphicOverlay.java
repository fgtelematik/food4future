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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic;

import static java.lang.Math.max;
import static java.lang.Math.min;

/** Graphic instance for rendering Barcode position and content information in an overlay view. */
public class QRCodeGraphicOverlay extends Graphic {

  private static final int MARKER_COLOR = Color.WHITE;
  private static final float STROKE_WIDTH = 4.0f;

  private final Paint rectPaint;
  private final Barcode barcode;

  QRCodeGraphicOverlay(GraphicOverlay overlay, Barcode barcode) {
    super(overlay);

    this.barcode = barcode;

    rectPaint = new Paint();
    rectPaint.setColor(MARKER_COLOR);
    rectPaint.setStyle(Paint.Style.STROKE);
    rectPaint.setStrokeWidth(STROKE_WIDTH);
  }

  /**
   * Draws the barcode block annotations for position and size on the supplied canvas.
   */
  @Override
  public void draw(Canvas canvas) {
    if (barcode == null) {
      throw new IllegalStateException("Attempting to draw a null barcode.");
    }

    // Draws the bounding box around the BarcodeBlock.
    RectF rect = new RectF(barcode.getBoundingBox());
    // If the image is flipped, the left will be translated to right, and the right to left.
    float x0 = translateX(rect.left);
    float x1 = translateX(rect.right);
    rect.left = min(x0, x1);
    rect.right = max(x0, x1);
    rect.top = translateY(rect.top);
    rect.bottom = translateY(rect.bottom);
    canvas.drawRect(rect, rectPaint);
  }
}
