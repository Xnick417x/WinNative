package com.winlator.cmod.core;

import android.content.Context;
import android.util.Log;

public abstract class GPUInformation {

    private static final String TAG = "GPUInformation";
    private static boolean nativeLibLoaded = false;

    public static boolean isAdrenoGPU(Context context) {
        String renderer = getRendererSafe(null, context);
        return renderer.toLowerCase().contains("adreno");
    }

    public static boolean isDriverSupported(String driverName, Context context) {
        try {
            if (!isAdrenoGPU(context) && !driverName.equals("System"))
                return false;

            String renderer = getRendererSafe(driverName, context);
            return !renderer.toLowerCase().contains("unknown");
        } catch (Throwable e) {
            Log.e(TAG, "Error checking driver support for: " + driverName, e);
            return false;
        }
    }

    /**
     * Safe wrapper around the native getRenderer that handles null returns and errors.
     */
    public static String getRendererSafe(String driverName, Context context) {
        try {
            if (!nativeLibLoaded) return "unknown";
            String renderer = getRenderer(driverName, context);
            return renderer != null ? renderer : "unknown";
        } catch (Throwable e) {
            Log.e(TAG, "Native getRenderer failed for driver: " + driverName, e);
            return "unknown";
        }
    }

    /**
     * Safe wrapper around enumerateExtensions that handles null and errors.
     */
    public static String[] enumerateExtensionsSafe(String driverName, Context context) {
        try {
            if (!nativeLibLoaded) return new String[0];
            String[] extensions = enumerateExtensions(driverName, context);
            return extensions != null ? extensions : new String[0];
        } catch (Throwable e) {
            Log.e(TAG, "Native enumerateExtensions failed for driver: " + driverName, e);
            return new String[0];
        }
    }

    public native static String getVulkanVersion(String driverName, Context context);
    public native static int getVendorID(String driverName, Context context);
    public native static String getRenderer(String driverName, Context context);

    public static String getRenderer(Context context) {
        return getRendererSafe(null, context);
    }

    public native static String[] enumerateExtensions(String driverName, Context context);

    static {
        try {
            System.loadLibrary("winlator");
            nativeLibLoaded = true;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load winlator native library", e);
            nativeLibLoaded = false;
        }
    }
}
