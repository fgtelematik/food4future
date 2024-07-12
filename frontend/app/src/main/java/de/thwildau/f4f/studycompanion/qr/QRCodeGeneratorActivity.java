package de.thwildau.f4f.studycompanion.qr;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import de.thwildau.f4f.studycompanion.R;
import de.thwildau.f4f.studycompanion.backend.BackendIO;
import de.thwildau.f4f.studycompanion.datamodel.SchemaProvider;
import de.thwildau.f4f.studycompanion.datamodel.User;

public class QRCodeGeneratorActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_INFOTEXT = "EXTRA_INFOTEXT";
    public static final String EXTRA_QR_DATA = "EXTRA_QR_DATA";
    public static final String EXTRA_SHOW_USER_QR = "EXTRA_SHOW_USER_QR";

    public static final String QR_PREFIX = "StudyCompanion.";
    public static final String QR_TOKEN_KEY = "tk";

    private static final int QR_HEIGHT = 800;
    private static final int QR_WIDTH = 800;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_generator);

        ImageView imgQr = findViewById(R.id.imgQR);
        TextView textInfotext = findViewById(R.id.textInfotext);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String infotext = getIntent().getStringExtra(EXTRA_INFOTEXT);

        String qrData = getIntent().getStringExtra(EXTRA_QR_DATA);



        if(qrData == null) {
            // When no QR Data is specified, the QRCodeGeneratorActivity was called from the Main Menu.
            // In this case, we present the participant id as QR code.

            User currentUser = BackendIO.getCurrentUser();
            if(currentUser == null) {
                // Nothing to show if no user id available. User was probably logged out recently.
                Toast.makeText(this, getString(R.string.message_session_expired), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            qrData = currentUser.id;

            title = getString(R.string.qrcode_generator_partqr_title);
            infotext = getString(R.string.qrcode_generator_partqr_infotext);

        } else {
            // Otherwise, we generate a StudyCompanion Download URL with the token in QR_CODE_DATA as URL parameter
            String token = qrData;

            qrData = QR_PREFIX + token; // old fallback version.

            // We now prefer creating an URL, which the user can also use for APK download
            Uri apkDownloadUrl = SchemaProvider.getDeviceConfig().getApkDownloadUrl();
            if (apkDownloadUrl != null) {
                qrData = apkDownloadUrl.buildUpon()
                        .appendQueryParameter(QR_TOKEN_KEY, token)
                        .build()
                        .toString();
            }

        }

        getSupportActionBar().setTitle(title != null ? title : getString(R.string.qrcode_generator_default_title));
        textInfotext.setText(infotext == null ? "" : infotext);

        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(qrData, BarcodeFormat.QR_CODE,QR_WIDTH,QR_HEIGHT);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            imgQr.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}