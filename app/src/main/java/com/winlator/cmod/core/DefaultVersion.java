package com.winlator.cmod.core;

import android.util.Log;

public abstract class DefaultVersion {
    private static final String TAG = "DefaultVersion";
    public static final String BOX64 = "0.3.7";
    public static final String WOWBOX64 = "0.3.7";
    public static final String FEXCORE = "2508";
    public static final String WRAPPER = "System";
    public static final String WRAPPER_ADRENO = "turnip25.1.0";
    public static final String DXVK = getDefaultDxvk();
    public static final String D8VK = "1.0";
    public static final String VKD3D = "None";

    private static String getDefaultDxvk() {
        try {
            String renderer = GPUInformation.getRendererSafe(null, null);
            if (renderer != null && renderer.contains("Mali")) {
                return "1.10.3";
            }
        } catch (Throwable e) {
            Log.w(TAG, "Error determining default DXVK version", e);
        }
        return "2.3.1";
    }
}