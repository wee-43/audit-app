package com.example.auditapp;

import android.os.Environment;
import android.util.Log;

import java.io.File;

public class FileUtils {
    public static void ensureBaseDirs() {
        createAuditFolders();
    }
    public static void createAuditFolders() {
        // Base folder under internal storage
        String basePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Audit/";

        File root   = new File(basePath);
        File master = new File(basePath + "master/");
        File output = new File(basePath + "output/");

        try {
            if (!root.exists())   root.mkdirs();
            if (!master.exists()) master.mkdirs();
            if (!output.exists()) output.mkdirs();

            Log.d("AuditApp", "Folders ensured at: " + basePath);

        } catch (Exception e) {
            Log.e("AuditApp", "Error creating folders", e);
        }

        // Update global paths so rest of the app uses same reference
        Paths.ROOT_DIR   = basePath;
        Paths.MASTER_DIR = basePath + "master/";
        Paths.OUTPUT_DIR = basePath + "output/";
    }
}
