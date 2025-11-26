package com.example.auditapp;


import static android.content.Context.RECEIVER_EXPORTED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.device.ScanManager;      // <-- ScanManager import (Urovo SDK)
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public class ScanActivity extends AppCompatActivity {

    private String auditor;
    private String rack;


    private TextView tvInfo, tvLastCode, tvCount;
    private Button btnFinishRack;
    private Button btnStartScan;
    private ListView lvScanned;

    private ArrayAdapter<String> adapter;
    private final ArrayList<String> scannedList = new ArrayList<>();
    private final HashSet<String> masterSet = new HashSet<>();

    // --------- ScanManager variables (REAL SCANNER) ---------
    private ScanManager scanManager;     // ScanManager reference
    private boolean scannerOpen = false;
    private Vibrator vibrator;

    // BroadcastReceiver to get scan results from ScanManager
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ScanManager.ACTION_DECODE.equals(intent.getAction())) return;

            String barcode = intent.getStringExtra(ScanManager.BARCODE_STRING_TAG);
            if (barcode == null) {
                byte[] data = intent.getByteArrayExtra(ScanManager.DECODE_DATA_TAG);
                if (data != null) {
                    barcode = new String(data).trim();
                }
            }

            if (barcode != null && !barcode.isEmpty()) {
                handleScannedCode(barcode);
                if (vibrator != null) vibrator.vibrate(50);
            }
        }
    };
    // --------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        auditor = getIntent().getStringExtra("auditor");
        rack    = getIntent().getStringExtra("rack");

        tvInfo       = findViewById(R.id.tvInfo);
        tvLastCode   = findViewById(R.id.tvLastCode);
        tvCount      = findViewById(R.id.tvCount);
        btnStartScan = findViewById(R.id.btnStartScan);
        btnFinishRack= findViewById(R.id.btnFinishRack);
        lvScanned    = findViewById(R.id.lvScannedItems);

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                scannedList);
        lvScanned.setAdapter(adapter);

        tvInfo.setText("Auditor: " + auditor + " | Rack: " + rack);
        tvLastCode.setText("Last scanned:");
        tvCount.setText("Total scanned: 0");

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        FileUtils.ensureBaseDirs();
        loadMasterData();      // read master txt for this auditor
        initScanner();         // <-- initialize ScanManager here
        btnStartScan.setOnClickListener(v -> startScan());
        btnFinishRack.setOnClickListener(v -> confirmFinishRack());
    }


    private void startScan() {
        if (scanManager == null || !scannerOpen) {
            Toast.makeText(this, "Scanner not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Start hardware decoding – laser turns on, PDA starts reading barcode
            scanManager.startDecode();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start scan", Toast.LENGTH_SHORT).show();
        }
    }

    // --------- Master data from txt file ---------
    private void loadMasterData() {
        File masterFile = new File(Paths.MASTER_DIR + auditor + ".txt");
        if (!masterFile.exists()) {
            Toast.makeText(this,
                    "Master file not found: " + masterFile.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(masterFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) masterSet.add(line);
            }
            Toast.makeText(this,
                    "Loaded " + masterSet.size() + " master codes",
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error reading master", Toast.LENGTH_LONG).show();
        }
    }

    // --------- ScanManager setup (REAL PDA SCANNER) ---------
//    private void initScanner() {
//        try {
//            scanManager = new ScanManager();       // create ScanManager object
//            scannerOpen = scanManager.openScanner(); // power on scanner
//
//            // Register broadcast to receive barcode data
//            IntentFilter filter = new IntentFilter();
//            filter.addAction(ScanManager.ACTION_DECODE);
//            registerReceiver(scanReceiver, filter);
//
//        } catch (Throwable t) {
//            t.printStackTrace();
//            Toast.makeText(this,
//                    "Scanner init failed (SDK / device issue)",
//                    Toast.LENGTH_LONG).show();
//        }
//    }



    private void initScanner() {
        try {
            scanManager = new ScanManager();
            scannerOpen = scanManager.openScanner();

            IntentFilter filter = new IntentFilter();
            filter.addAction(ScanManager.ACTION_DECODE);

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                // For Android 13+ – specify flag
                registerReceiver(scanReceiver, filter, RECEIVER_EXPORTED);
            } else {
                // For older devices (like your PDA – Android 7.1)
                registerReceiver(scanReceiver, filter);
            }

        } catch (Throwable t) {
            t.printStackTrace();
            Toast.makeText(this,
                    "Scanner init failed (SDK / device issue)",
                    Toast.LENGTH_LONG).show();
        }
    }

    // --------------------------------------------------------

    private void handleScannedCode(String code) {
        if (!masterSet.contains(code)) {
            new AlertDialog.Builder(this)
                    .setTitle("Not found in master")
                    .setMessage("Code: " + code)
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        scannedList.add(code);
        adapter.notifyDataSetChanged();

        tvLastCode.setText("Last scanned: " + code);
        tvCount.setText("Total scanned: " + scannedList.size());
    }

    private void confirmFinishRack() {
        if (scannedList.isEmpty()) {
            Toast.makeText(this, "No items scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Finish rack?")
                .setMessage("Total scanned: " + scannedList.size())
                .setPositiveButton("Yes", (dialog, which) -> finishRack())
                .setNegativeButton("No", null)
                .show();
    }

    private void finishRack() {
        long now = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date(now));

        String baseName = auditor + "_" + rack + "_" + timeStamp;

        File summaryFile = new File(Paths.OUTPUT_DIR +
                baseName + "_summary.txt");
        File itemsFile = new File(Paths.OUTPUT_DIR +
                baseName + "_items.txt");

        try {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(itemsFile))) {
                for (String code : scannedList) {
                    bw.write(code);
                    bw.newLine();
                }
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(summaryFile))) {
                String line = auditor + "/" +
                        rack + "/" +
                        Paths.OUTPUT_DIR + "/" +
                        timeStamp + "/" +
                        scannedList.size();
                bw.write(line);
                bw.newLine();
            }

            Toast.makeText(this,
                    "Saved: " + itemsFile.getName(),
                    Toast.LENGTH_LONG).show();
            finish();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving files", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(scanReceiver);   // stop receiving scans
        } catch (Exception ignored) {}

        // Close ScanManager / scanner hardware
        if (scanManager != null && scannerOpen) {
            scanManager.stopDecode();
            scanManager.closeScanner();
        }
    }
}
