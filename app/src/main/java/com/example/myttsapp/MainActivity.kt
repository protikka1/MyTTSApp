package com.example.myttsapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isTtsReady by mutableStateOf(false)
    private var currentLocale by mutableStateOf(Locale.US)
    
    private var translator: Translator? = null
    private var isTranslating by mutableStateOf(false)
    private var isModelReady by mutableStateOf(false)

    private val supportedLocales = listOf(
        Locale.US,
        Locale.forLanguageTag("es-ES"),
        Locale.FRANCE,
        Locale.GERMANY,
        Locale.ITALY,
        Locale.CHINA,
        Locale.JAPAN,
        Locale.forLanguageTag("ar"),
        Locale.forLanguageTag("bn"),
        Locale.forLanguageTag("hi"),
        Locale.forLanguageTag("ur"),
        Locale.forLanguageTag("pt"),
        Locale.forLanguageTag("ru")
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val msg = if (isGranted) "Permission Granted" else "Permission Denied"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        
        setContent {
            val context = LocalContext.current
            val systemInDark = isSystemInDarkTheme()
            var isDarkMode by remember { mutableStateOf(systemInDark) }

            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                isDarkMode -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                TTSAppContainer(
                    isReady = isTtsReady,
                    isModelReady = isModelReady,
                    isDarkMode = isDarkMode,
                    isTranslating = isTranslating,
                    currentLocale = currentLocale,
                    supportedLocales = supportedLocales,
                    onToggleTheme = { isDarkMode = !isDarkMode },
                    onLanguageChange = { locale ->
                        currentLocale = locale
                        setupTranslator(locale)
                    },
                    onSpeak = { text, pitch, speed ->
                        speakOut(text, currentLocale, pitch, speed)
                    },
                    onTranslateAndSpeak = { text, pitch, speed ->
                        translateAndAction(text, pitch, speed, isSave = false)
                    },
                    onSaveAudio = { text, pitch, speed ->
                        if (currentLocale.language == Locale.ENGLISH.language) {
                            saveSpeechToFile(text, currentLocale, pitch, speed)
                        } else {
                            translateAndAction(text, pitch, speed, isSave = true)
                        }
                    },
                    onRequestPermission = {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRetryDownload = { setupTranslator(currentLocale) }
                )
            }
        }
        
        setupTranslator(currentLocale)
    }

    private fun setupTranslator(targetLocale: Locale) {
        translator?.close()
        isModelReady = false
        
        val targetLang = TranslateLanguage.fromLanguageTag(targetLocale.language)
        
        if (targetLang == null || targetLang == TranslateLanguage.ENGLISH) {
            isModelReady = true
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        
        translator = Translation.getClient(options)
        
        // Ensure no conditions block the download (allow cellular)
        val conditions = DownloadConditions.Builder().build()
            
        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                isModelReady = true
                Log.d("TTSPro", "Model ready for ${targetLocale.displayLanguage}")
            }
            ?.addOnFailureListener { e ->
                Log.e("TTSPro", "Model download failed", e)
                Toast.makeText(this, "Failed to download data. Check Internet!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun translateAndAction(text: String, pitch: Float, speed: Float, isSave: Boolean) {
        if (text.isBlank()) return
        
        if (!isModelReady && currentLocale != Locale.US) {
            Toast.makeText(this, "Still downloading language data... please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        isTranslating = true
        translator?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                isTranslating = false
                if (isSave) {
                    saveSpeechToFile(translatedText, currentLocale, pitch, speed)
                } else {
                    speakOut(translatedText, currentLocale, pitch, speed)
                }
            }
            ?.addOnFailureListener { e ->
                isTranslating = false
                Log.e("TTSPro", "Translation failed", e)
                Toast.makeText(this, "Translation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun speakOut(text: String, locale: Locale, pitch: Float, speed: Float) {
        tts?.apply {
            setPitch(pitch)
            setSpeechRate(speed)
            val result = setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this@MainActivity, "Voice data for ${locale.displayLanguage} is not on your phone.", Toast.LENGTH_LONG).show()
            }
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
        }
    }

    private fun saveSpeechToFile(text: String, locale: Locale, pitch: Float, speed: Float) {
        val fileName = "TTS_${System.currentTimeMillis()}.wav"
        val tempFile = File(getExternalFilesDir(null), fileName)
        
        tts?.apply {
            setPitch(pitch)
            setSpeechRate(speed)
            setLanguage(locale)
            val result = synthesizeToFile(text, null, tempFile, "save_id")
            
            if (result == TextToSpeech.SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/TTSPro")
                    }
                    val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { dest ->
                        contentResolver.openOutputStream(dest)?.use { out ->
                            tempFile.inputStream().use { it.copyTo(out) }
                        }
                        Toast.makeText(this@MainActivity, "Saved to Music/TTSPro", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        translator?.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TTSAppContainer(
    isReady: Boolean,
    isModelReady: Boolean,
    isDarkMode: Boolean,
    isTranslating: Boolean,
    currentLocale: Locale,
    supportedLocales: List<Locale>,
    onToggleTheme: () -> Unit,
    onLanguageChange: (Locale) -> Unit,
    onSpeak: (String, Float, Float) -> Unit,
    onTranslateAndSpeak: (String, Float, Float) -> Unit,
    onSaveAudio: (String, Float, Float) -> Unit,
    onRequestPermission: () -> Unit,
    onRetryDownload: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showUserGuide by remember { mutableStateOf(false) }

    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                DrawerContent(
                    pitch = pitch,
                    onPitchChange = { pitch = it },
                    speed = speed,
                    onSpeedChange = { speed = it },
                    onRequestPermission = onRequestPermission,
                    onShowGuide = { showUserGuide = true; scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text("TTS Pro", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Settings")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            MainScreenContent(
                padding = padding,
                isReady = isReady,
                isModelReady = isModelReady,
                isTranslating = isTranslating,
                currentLocale = currentLocale,
                supportedLocales = supportedLocales,
                onLanguageChange = onLanguageChange,
                onSpeak = { onSpeak(it, pitch, speed) },
                onTranslateAndSpeak = { onTranslateAndSpeak(it, pitch, speed) },
                onSaveAudio = { onSaveAudio(it, pitch, speed) },
                onRetryDownload = onRetryDownload
            )
        }
    }
}

@Composable
fun UserGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val guideText = remember {
        try {
            context.assets.open("user_guide.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "User guide not found."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎙️ User Guide", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(guideText)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun DrawerHeader() {
    Column(modifier = Modifier.padding(24.dp)) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Text("Audio Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun DrawerContent(
    pitch: Float, 
    onPitchChange: (Float) -> Unit, 
    speed: Float, 
    onSpeedChange: (Float) -> Unit, 
    onRequestPermission: () -> Unit,
    onShowGuide: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(24.dp)) {
        SettingSlider("Pitch", pitch, onPitchChange)
        Spacer(modifier = Modifier.height(20.dp))
        SettingSlider("Speed", speed, onSpeedChange)
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Support", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        NavigationDrawerItem(
            label = { Text("User Guide") },
            selected = false,
            onClick = onShowGuide,
            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
        
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        NavigationDrawerItem(
            label = { Text("Mic Permission") },
            selected = false,
            onClick = onRequestPermission,
            icon = { Icon(Icons.Default.Mic, null) },
            badge = { Text(if (micGranted) "OK" else "Fix") },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun MainScreenContent(
    padding: PaddingValues,
    isReady: Boolean,
    isModelReady: Boolean,
    isTranslating: Boolean,
    currentLocale: Locale,
    supportedLocales: List<Locale>,
    onLanguageChange: (Locale) -> Unit,
    onSpeak: (String) -> Unit,
    onTranslateAndSpeak: (String) -> Unit,
    onSaveAudio: (String) -> Unit,
    onRetryDownload: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var showLanguageMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = { showLanguageMenu = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, null)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target Language", style = MaterialTheme.typography.labelSmall)
                    Text(currentLocale.displayLanguage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.UnfoldMore, null)
            }
        }

        DropdownMenu(expanded = showLanguageMenu, onDismissRequest = { showLanguageMenu = false }) {
            supportedLocales.forEach { locale ->
                DropdownMenuItem(
                    text = { Text(locale.getDisplayName(Locale.ENGLISH)) },
                    onClick = { onLanguageChange(locale); showLanguageMenu = false }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Enter text in English...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSpeak(text) },
                        enabled = isReady && text.isNotBlank(),
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("Speak", modifier = Modifier.padding(start = 4.dp))
                    }

                    FilledTonalButton(
                        onClick = { onTranslateAndSpeak(text) },
                        enabled = isReady && isModelReady && text.isNotBlank() && !isTranslating,
                        modifier = Modifier.weight(1.2f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Translate, null)
                            Text("Tr. & Speak", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = { onSaveAudio(text) },
                        enabled = isReady && (currentLocale == Locale.US || isModelReady) && text.isNotBlank(),
                        modifier = Modifier.weight(0.8f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Save, null)
                    }
                }
            }
        }

        AnimatedVisibility(visible = !isReady || (!isModelReady && currentLocale != Locale.US)) {
            Column(modifier = Modifier.padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                val msg = if (!isReady) "Initializing TTS..." else "Downloading Language Data..."
                Text(msg, modifier = Modifier.padding(top = 12.dp))
                
                if (!isModelReady && currentLocale != Locale.US) {
                    TextButton(onClick = onRetryDownload) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.weight(1f))
            Text("%.1f".format(value))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0.5f..2.0f)
    }
}
