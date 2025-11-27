package com.example.auditapp;

import android.os.Environment;

public class Paths {
    public static String ROOT_DIR = Environment.getExternalStorageDirectory()
            .getAbsolutePath() + "/Audit/";
    public static String MASTER_DIR = ROOT_DIR + "master/";
    public static String OUTPUT_DIR = ROOT_DIR + "output/";
    public static String OUTPUT_SCANNED_DIR = OUTPUT_DIR + "scanned_data/";
}
