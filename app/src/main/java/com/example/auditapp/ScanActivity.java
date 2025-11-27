package com.example.auditapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.device.ScanManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import static android.content.Context.RECEIVER_EXPORTED;

public class ScanActivity extends AppCompatActivity {

    // Values received from MainActivity
    private String auditor;
    private String rack;
    private boolean continueExisting;

    // UI elements
    private TextView tvInfo, tvLastCode, tvCount;
    private Button btnStartScan, btnStopScan, btnFinishRack, btnAddManual;
    private Spinner spMasterFiles, spDelay;
    private EditText etManualCode, etCustomDelay;
    private ListView lvScanned;

    // Scanned + master
    private ArrayAdapter<String> adapter;
    private final ArrayList<String> scannedList = new ArrayList<>();
    private final HashSet<String> masterSet = new HashSet<>();

    // Scanner SDK
    private ScanManager scanManager;
    private boolean scannerOpen = false;
    private Vibrator vibrator;

    // Auto scan control
    private boolean autoScanEnabled = false;     // true = continuous mode ON
    private boolean scanningInProgress = false;  // true = decode is active
    private long autoDelayMs = 1000;             // default delay = 1 sec

    // Auto cancel timeout = 8 sec (if barcode not read)
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable stopScanRunnable;
    private static final long SCAN_TIMEOUT_MS = 8000;

    private String currentMasterFileName = null;

    // Broadcast receiver gets decoded barcode from hardware scanner
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ScanManager.ACTION_DECODE.equals(intent.getAction())) return;

            // Get scanned value
            String barcode = intent.getStringExtra(ScanManager.BARCODE_STRING_TAG);
            if (barcode == null) {
                byte[] data = intent.getByteArrayExtra(ScanManager.DECODE_DATA_TAG);
                if (data != null) barcode = new String(data).trim();
            }

            if (barcode != null && !barcode.isEmpty()) {
                stopScanTimeout();              // stop timeout countdown
                scanningInProgress = false;     // scan cycle finished

                boolean ok = handleScannedCode(barcode); // validate + add
                if (vibrator != null) vibrator.vibrate(50);

                // If OK + auto mode = start next scan automatically
                if (ok && autoScanEnabled) {
                    startNextScanAfterDelay();
                } else {
                    safeStopDecode(); // error or stopped -> stop laser
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        /* 🌟 Get values from MainActivity */
        auditor = getIntent().getStringExtra("auditor");
        rack = getIntent().getStringExtra("rack");
        continueExisting = getIntent().getBooleanExtra("continueExisting", false);

        /* 🌟 Bind UI */
        tvInfo = findViewById(R.id.tvInfo);
        tvLastCode = findViewById(R.id.tvLastCode);
        tvCount = findViewById(R.id.tvCount);
        btnStartScan = findViewById(R.id.btnStartScan);
        btnStopScan = findViewById(R.id.btnStopScan);
        btnFinishRack = findViewById(R.id.btnFinishRack);
        btnAddManual = findViewById(R.id.btnAddManual);
        spMasterFiles = findViewById(R.id.spMasterFiles);
        spDelay = findViewById(R.id.spDelay);
        etManualCode = findViewById(R.id.etManualCode);
        etCustomDelay = findViewById(R.id.etCustomDelay);
        lvScanned = findViewById(R.id.lvScannedItems);

        /* 🌟 Setup list */
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, scannedList);
        lvScanned.setAdapter(adapter);

        tvInfo.setText("Auditor: " + auditor + " | Rack: " + rack);
        tvLastCode.setText("Last scanned:");
        tvCount.setText("Total scanned: 0");
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        /* 🌟 Setup folders and scanner + dropdown UI */
        FileUtils.ensureBaseDirs();
        initMasterDropdown();
        initScanner();
        initDelayDropdown();

        /* 🌟 Load previous file if user selected "Continue Rack" */
        if (continueExisting) loadExistingScannedData();

        /* 🌟 START button */
        btnStartScan.setOnClickListener(v -> {
            // If custom selected -> convert user text seconds to ms
            if (autoDelayMs == -1) {
                String custom = etCustomDelay.getText().toString().trim();
                if (custom.isEmpty()) {
                    Toast.makeText(this, "Enter custom delay", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    float sec = Float.parseFloat(custom);
                    autoDelayMs = (long) (sec * 1000);
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid seconds", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            autoScanEnabled = true;
            startScanOnce();
            Toast.makeText(this, "Auto scan started", Toast.LENGTH_SHORT).show();
        });

        /* 🌟 STOP button */
        btnStopScan.setOnClickListener(v -> stopAutoScan());

        /* 🌟 FINISH button */
        btnFinishRack.setOnClickListener(v -> confirmFinishRack());

        /* 🌟 Manual input */
        btnAddManual.setOnClickListener(v -> addManualCode());
    }

    /* ====================================================================== */
    /*  MASTER FILE DROPDOWN  */
    /* ====================================================================== */

    private void initMasterDropdown() {
        File[] files = new File(Paths.MASTER_DIR).listFiles(
                pathname -> pathname.isFile() && pathname.getName().endsWith(".txt")
        );
        ArrayList<String> names = new ArrayList<>();

        if (files != null && files.length > 0) {
            Arrays.sort(files);
            for (File f : files) names.add(f.getName());
        } else names.add("NO MASTER FILES");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMasterFiles.setAdapter(adapter);

        spMasterFiles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                 android.view.View view, int position, long id) {
                String name = names.get(position);
                if (name.equals("NO MASTER FILES")) {
                    currentMasterFileName = null;
                    masterSet.clear();
                } else {
                    currentMasterFileName = name;
                    loadMasterData(name);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void loadMasterData(String fileName) {
        masterSet.clear();
        try (BufferedReader br = new BufferedReader(
                new FileReader(Paths.MASTER_DIR + fileName)
        )) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) masterSet.add(line.trim());
            }
            Toast.makeText(this, "Loaded " + masterSet.size() + " items", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error reading master", Toast.LENGTH_LONG).show();
        }
    }

    /* ====================================================================== */
    /*  CONTINUE EXISTING SCAN FILE  */
    /* ====================================================================== */
    private void loadExistingScannedData() {
        File dir = new File(Paths.OUTPUT_SCANNED_DIR);
        File[] files = dir.listFiles(pathname ->
                pathname.getName().startsWith(auditor + "_" + rack + "_")
                        && pathname.getName().endsWith("_items.txt")
        );
        if (files == null || files.length == 0) return;

        // pick latest
        File latest = files[0];
        for (File f : files) if (f.lastModified() > latest.lastModified()) latest = f;

        try (BufferedReader br = new BufferedReader(new FileReader(latest))) {
            String line;
            while ((line = br.readLine()) != null) scannedList.add(line.trim());
            adapter.notifyDataSetChanged();
            tvCount.setText("Total scanned: " + scannedList.size());
        } catch (Exception ignored) {}
    }

    /* ====================================================================== */
    /*  SCAN DELAY OPTIONS (1s / 2s / 3s / CUSTOM)  */
    /* ====================================================================== */
    private void initDelayDropdown() {
        String[] options = {"1 sec", "2 sec", "3 sec", "Custom"};
        ArrayAdapter<String> arr = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        arr.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDelay.setAdapter(arr);

        spDelay.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                 android.view.View view, int position, long id) {
                if (position == 0) autoDelayMs = 1000;
                else if (position == 1) autoDelayMs = 2000;
                else if (position == 2) autoDelayMs = 3000;
                else autoDelayMs = -1; // custom (we'll read from EditText later)
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /* ====================================================================== */
    /*  SCANNER INIT + AUTO/ TIMEOUT  */
    /* ====================================================================== */
    private void initScanner() {
        try {
            scanManager = new ScanManager();
            scannerOpen = scanManager.openScanner();

            IntentFilter filter = new IntentFilter(ScanManager.ACTION_DECODE);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(scanReceiver, filter, RECEIVER_EXPORTED);
            else registerReceiver(scanReceiver, filter);

        } catch (Exception e) {
            Toast.makeText(this, "Scanner init failed", Toast.LENGTH_LONG).show();
        }
    }

    /** Start one scan cycle */
    private void startScanOnce() {
        if (!scannerOpen || scanManager == null) {
            Toast.makeText(this, "Scanner not ready", Toast.LENGTH_SHORT).show();
            autoScanEnabled = false;
            return;
        }
        if (scanningInProgress) return;

        scanningInProgress = true;
        scanManager.startDecode();
        startTimeout();
    }

    /** Schedule next scan automatically */
    private void startNextScanAfterDelay() {
        handler.postDelayed(this::startScanOnce, autoDelayMs);
    }

    /** Timeout if no barcode within 8 sec */
    private void startTimeout() {
        stopScanTimeout();
        stopScanRunnable = () -> {
            scanningInProgress = false;
            safeStopDecode();
        };
        handler.postDelayed(stopScanRunnable, SCAN_TIMEOUT_MS);
    }

    private void stopScanTimeout() {
        if (stopScanRunnable != null) handler.removeCallbacks(stopScanRunnable);
    }

    /** Stop hardware decode */
    private void safeStopDecode() {
        try { if (scanManager != null) scanManager.stopDecode(); }
        catch (Exception ignored) {}
    }

    /** Stop auto scan fully */
    private void stopAutoScan() {
        autoScanEnabled = false;
        scanningInProgress = false;
        stopScanTimeout();
        safeStopDecode();
    }

    /* ====================================================================== */
    /*  MANUAL CODE ENTRY  */
    /* ====================================================================== */
    private void addManualCode() {
        String code = etManualCode.getText().toString().trim();
        if (!code.isEmpty()) {
            boolean ok = handleScannedCode(code);
            if (ok) etManualCode.setText("");
        }
    }

    /* ====================================================================== */
    /*  VALIDATE SCAN + ADD TO LIST  */
    /** returns true → OK, false → invalid */
    /* ====================================================================== */
    private boolean handleScannedCode(String code) {
        if (currentMasterFileName == null || masterSet.isEmpty()) {
            Toast.makeText(this, "No master selected", Toast.LENGTH_SHORT).show();
            return false;
        }

        // ❗ If not in master → stop auto scan + show dialog
        if (!masterSet.contains(code)) {
            stopAutoScan();
            new AlertDialog.Builder(this)
                    .setTitle("NOT in master")
                    .setMessage(code)
                    .setPositiveButton("OK", null)
                    .show();
            return false;
        }

        // Valid → add + refresh UI
        scannedList.add(code);
        adapter.notifyDataSetChanged();
        tvLastCode.setText("Last: " + code);
        tvCount.setText("Total scanned: " + scannedList.size());
        return true;
    }

    /* ====================================================================== */
    /*  FINISH + WRITE FILES  */
    /* ====================================================================== */
    private void confirmFinishRack() {
        if (scannedList.isEmpty()) {
            Toast.makeText(this, "No items scanned", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Finish rack?")
                .setMessage("Total scanned: " + scannedList.size())
                .setPositiveButton("Yes", (d, w) -> saveFiles())
                .setNegativeButton("No", null)
                .show();
    }

    private void saveFiles() {
        long now = System.currentTimeMillis();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date(now));
        String base = auditor + "_" + rack + "_" + ts;

        File items = new File(Paths.OUTPUT_SCANNED_DIR + base + "_items.txt");
        File summary = new File(Paths.OUTPUT_DIR + base + "_summary.txt");

        try {
            // save scanned list
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(items))) {
                for (String code : scannedList) bw.write(code + "\n");
            }
            // save summary
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(summary))) {
                bw.write(auditor + "/" + rack + "/" +
                        Paths.OUTPUT_SCANNED_DIR + "/" + ts + "/" + scannedList.size());
            }

            Toast.makeText(this, "Saved: " + items.getName(), Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error saving files", Toast.LENGTH_LONG).show();
        }
    }

    /* ====================================================================== */
    /*  CLEAN UP  */
    /* ====================================================================== */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoScan();
        try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
        if (scannerOpen && scanManager != null) {
            safeStopDecode();
            scanManager.closeScanner();
        }
    }
}
