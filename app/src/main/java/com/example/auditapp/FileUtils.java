package com.example.auditapp;

import android.util.Log;

import java.io.File;

public class FileUtils {

    // You can call either of these; ensureBaseDirs() kept for old calls
    public static void ensureBaseDirs() {
        createAuditFolders();
    }

    public static void createAuditFolders() {
        File root   = new File(Paths.ROOT_DIR);
        File master = new File(Paths.MASTER_DIR);
        File output = new File(Paths.OUTPUT_DIR);
        File scanned = new File(Paths.OUTPUT_SCANNED_DIR);

        try {
            if (!root.exists())   root.mkdirs();
            if (!master.exists()) master.mkdirs();
            if (!output.exists()) output.mkdirs();
            if (!scanned.exists()) scanned.mkdirs();

            Log.d("AuditApp", "Folders ensured");
        } catch (Exception e) {
            Log.e("AuditApp", "Error creating folders", e);
        }
    }
}
