package com.winlator.cmod

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xenvironment.ImageFsInstaller

class SetupWizardActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "winnative_setup"
        private const val KEY_SETUP_COMPLETE = "setup_complete"

        fun isSetupComplete(context: android.content.Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_SETUP_COMPLETE, false)
        }

        fun markSetupComplete(context: android.content.Context) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
        }
    }

    private val storageGranted = mutableStateOf(false)
    private val notifGranted = mutableStateOf(false)
    private val installing = mutableStateOf(false)
    private val installProgress = mutableIntStateOf(0)
    private val installDone = mutableStateOf(false)
    private val installError = mutableStateOf<String?>(null)

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storageGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted.value = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip wizard if already complete and ImageFS is valid
        if (isSetupComplete(this) && ImageFs.find(this).isValid) {
            launchApp()
            return
        }

        // Check current permission state
        storageGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        notifGranted.value = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SetupScreen()
            }
        }
    }

    @Composable
    private fun SetupScreen() {
        val storage by storageGranted
        val notif by notifGranted
        val isInstalling by installing
        val progress by installProgress
        val done by installDone
        val error by installError

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Welcome to WinNative",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6EDF3),
                    textAlign = TextAlign.Center
                )

                Text(
                    "A few quick things before we get started.",
                    fontSize = 13.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.widthIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PermissionRow(
                        label = "File Access",
                        granted = storage,
                        required = true,
                        onRequest = { requestFileAccess() },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                    PermissionRow(
                        label = "Notifications",
                        granted = notif,
                        required = false,
                        onRequest = { requestNotifications() },
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                }

                // Install progress
                if (isInstalling) {
                    Text("Installing system files...", color = Color(0xFF8B949E), fontSize = 13.sp)
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(6.dp),
                        color = Color(0xFF57CBDE),
                        trackColor = Color(0xFF21262D),
                    )
                    Text("$progress%", color = Color(0xFF57CBDE), fontSize = 12.sp)
                }

                if (error != null) {
                    Text(error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                }

                Spacer(Modifier.weight(1f))

                // Finish button
                Button(
                    onClick = {
                        if (!isInstalling) {
                            finishSetup()
                        }
                    },
                    enabled = storage && !isInstalling,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF57CBDE),
                        disabledContainerColor = Color(0xFF21262D)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (done) "Launch App" else if (isInstalling) "Installing..." else "Finish Setup",
                        fontWeight = FontWeight.Bold,
                        color = if (storage && !isInstalling) Color.Black else Color(0xFF8B949E)
                    )
                }
            }
        }
    }

    @Composable
    private fun PermissionRow(
        label: String,
        granted: Boolean,
        required: Boolean,
        onRequest: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .background(Color(0xFF161B22), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label + if (!required) " (optional)" else "",
                    color = Color(0xFFE6EDF3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (granted) {
                Text("✓", color = Color(0xFF3FB950), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                TextButton(onClick = onRequest) {
                    Text("Grant", color = Color(0xFF57CBDE))
                }
            }
        }
    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            manageStorageLauncher.launch(intent)
        } else {
            // Pre-R: request WRITE_EXTERNAL_STORAGE
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                100
            )
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            storageGranted.value = grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun finishSetup() {
        val imageFs = ImageFs.find(this)
        if (imageFs.isValid && imageFs.version >= ImageFsInstaller.LATEST_VERSION.toInt()) {
            markSetupComplete(this)
            launchApp()
            return
        }

        installing.value = true
        installError.value = null
        val rootDir = imageFs.rootDir

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            try {
                // Clear and recreate root dir (matches original installFromAssets)
                clearRootDir(rootDir)

                val compressionRatio = 22
                val assetSize = com.winlator.cmod.core.FileUtils.getSize(this, "imagefs.txz")
                // available() can return 0 for large assets; use fallback estimate
                val contentLength = if (assetSize > 0) {
                    (assetSize * (100.0f / compressionRatio)).toLong()
                } else {
                    800_000_000L // ~800MB estimated uncompressed
                }
                val totalSize = java.util.concurrent.atomic.AtomicLong()

                val listener = com.winlator.cmod.core.OnExtractFileListener { file, size ->
                    if (size > 0) {
                        val total = totalSize.addAndGet(size)
                        val pct = ((total.toFloat() / contentLength) * 100).toInt().coerceIn(0, 100)
                        runOnUiThread { installProgress.intValue = pct }
                    }
                    file
                }

                val success = com.winlator.cmod.core.TarCompressorUtils.extract(
                    com.winlator.cmod.core.TarCompressorUtils.Type.XZ,
                    this, "imagefs.txz", rootDir, listener
                )

                if (success) {
                    // Install wine from assets
                    try {
                        val versions = resources.getStringArray(R.array.wine_entries)
                        for (version in versions) {
                            val outFile = java.io.File(rootDir, "/opt/$version")
                            outFile.mkdirs()
                            com.winlator.cmod.core.TarCompressorUtils.extract(
                                com.winlator.cmod.core.TarCompressorUtils.Type.XZ,
                                this, "$version.txz", outFile
                            )
                        }
                    } catch (_: Exception) {}

                    // Install drivers from assets
                    try {
                        ImageFsInstaller.installDriversFromAssets(this as? MainActivity)
                    } catch (_: Exception) {}

                    imageFs.createImgVersionFile(ImageFsInstaller.LATEST_VERSION.toInt())
                    runOnUiThread {
                        installDone.value = true
                        installing.value = false
                        markSetupComplete(this)
                        launchApp()
                    }
                } else {
                    runOnUiThread {
                        installing.value = false
                        installError.value = "Extraction failed. Please check available storage and try again."
                    }
                }
            } catch (e: Exception) {
                val msg = e.stackTraceToString().take(200)
                runOnUiThread {
                    installing.value = false
                    installError.value = "Error: ${e.message}\n$msg"
                }
            }
        }
    }

    /** Matches ImageFsInstaller.clearRootDir logic */
    private fun clearRootDir(rootDir: java.io.File) {
        if (rootDir.isDirectory) {
            rootDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name == "home") return@forEach
                com.winlator.cmod.core.FileUtils.delete(file)
            }
        } else {
            rootDir.mkdirs()
        }
    }

    private fun launchApp() {
        startActivity(Intent(this, UnifiedActivity::class.java))
        finish()
    }
}
