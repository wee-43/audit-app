package com.example.auditapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_STORAGE = 100;

    private EditText etAuditorName, etRackNumber;
    private Button btnStartAudit, btnContinueAudit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAuditorName   = findViewById(R.id.etAuditorName);
        etRackNumber    = findViewById(R.id.etRackNumber);
        btnStartAudit   = findViewById(R.id.btnStartAudit);
        btnContinueAudit= findViewById(R.id.btnContinueAudit);

        checkStoragePermission();
        FileUtils.ensureBaseDirs();

        btnStartAudit.setOnClickListener(v -> startAudit(false));
        btnContinueAudit.setOnClickListener(v -> startAudit(true));
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    REQ_STORAGE);
        }
    }

    private void startAudit(boolean continueExisting) {
        String auditor = etAuditorName.getText().toString().trim();
        String rack    = etRackNumber.getText().toString().trim();

        if (auditor.isEmpty() || rack.isEmpty()) {
            Toast.makeText(this, "Enter auditor and rack", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, ScanActivity.class);
        i.putExtra("auditor", auditor);
        i.putExtra("rack", rack);
        i.putExtra("continueExisting", continueExisting);
        startActivity(i);
    }
}
