package com.example.myttsapp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    private var downloadProgress by mutableStateOf<String?>(null)

    // Speech-to-Text
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening by mutableStateOf(false)
    private var recognizedText = mutableStateOf("")
    
    // Contact Picker
    private var pickedContactName = mutableStateOf("")

    private val allSupportedLocales by lazy {
        TranslateLanguage.getAllLanguages()
            .map { Locale.forLanguageTag(it) }
            .filter { it.language != "en" }
            .sortedBy { it.getDisplayName(Locale.ENGLISH) }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        val msg = if (isGranted) "Permission Granted" else "Permission Denied"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { getContactName(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        initSpeechRecognizer()
        
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
                    isListening = isListening,
                    recognizedText = recognizedText.value,
                    pickedContactName = pickedContactName.value,
                    currentLocale = currentLocale,
                    supportedLocales = allSupportedLocales,
                    downloadProgress = downloadProgress,
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
                    onStartListening = { startListening() },
                    onStopListening = { stopListening() },
                    onPickContact = { 
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            pickContactLauncher.launch(null)
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    onRequestPermission = { permission ->
                        requestPermissionLauncher.launch(permission)
                    },
                    onRetryDownload = { setupTranslator(currentLocale) }
                )
            }
        }
        setupTranslator(currentLocale)
    }

    private fun getContactName(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (nameIndex != -1) {
                    pickedContactName.value = it.getString(nameIndex)
                    recognizedText.value = "" 
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText.value = matches[0]
                    pickedContactName.value = ""
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    private fun setupTranslator(targetLocale: Locale) {
        translator?.close()
        isModelReady = false
        
        val targetLang = TranslateLanguage.fromLanguageTag(targetLocale.language)
        if (targetLang == null || targetLang == TranslateLanguage.ENGLISH) {
            isModelReady = true
            downloadProgress = null
            return
        }

        initializeTranslator(targetLang)
        downloadProgress = "Starting download for ${targetLocale.displayLanguage}..."
        
        val conditions = DownloadConditions.Builder().build()
        translator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                isModelReady = true
                downloadProgress = null
            }
            ?.addOnFailureListener { e ->
                isModelReady = false
                downloadProgress = "Download failed."
            }
    }

    private fun initializeTranslator(targetLang: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()
        translator = Translation.getClient(options)
    }

    private fun translateAndAction(text: String, pitch: Float, speed: Float, isSave: Boolean) {
        if (text.isBlank() || !isModelReady) return
        isTranslating = true
        translator?.translate(text)
            ?.addOnSuccessListener { translatedText ->
                isTranslating = false
                if (isSave) saveSpeechToFile(translatedText, currentLocale, pitch, speed)
                else speakOut(translatedText, currentLocale, pitch, speed)
            }
            ?.addOnFailureListener { e ->
                isTranslating = false
            }
    }

    private fun speakOut(text: String, locale: Locale, pitch: Float, speed: Float) {
        tts?.apply {
            setPitch(pitch)
            setSpeechRate(speed)
            setLanguage(locale)
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
            if (synthesizeToFile(text, null, tempFile, "save_id") == TextToSpeech.SUCCESS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/TTSPro")
                    }
                    val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
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
        if (status == TextToSpeech.SUCCESS) isTtsReady = true
    }

    override fun onDestroy() {
        tts?.shutdown()
        translator?.close()
        speechRecognizer?.destroy()
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
    isListening: Boolean,
    recognizedText: String,
    pickedContactName: String,
    currentLocale: Locale,
    supportedLocales: List<Locale>,
    downloadProgress: String?,
    onToggleTheme: () -> Unit,
    onLanguageChange: (Locale) -> Unit,
    onSpeak: (String, Float, Float) -> Unit,
    onTranslateAndSpeak: (String, Float, Float) -> Unit,
    onSaveAudio: (String, Float, Float) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onPickContact: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onRetryDownload: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showUserGuide by remember { mutableStateOf(false) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    if (showUserGuide) UserGuideDialog(onDismiss = { showUserGuide = false })

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
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                    actions = { IconButton(onClick = onToggleTheme) { Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null) } },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            MainScreenContent(padding, isReady, isModelReady, isTranslating, isListening, recognizedText, pickedContactName, currentLocale, supportedLocales, downloadProgress, onLanguageChange, { onSpeak(it, pitch, speed) }, { onTranslateAndSpeak(it, pitch, speed) }, { onSaveAudio(it, pitch, speed) }, onStartListening, onStopListening, onPickContact, onRetryDownload)
        }
    }
}

@Composable
fun UserGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val guideText = remember { try { context.assets.open("user_guide.txt").bufferedReader().use { it.readText() } } catch (e: Exception) { "Guide not found." } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("User Guide") }, text = { Text(guideText) }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }, shape = RoundedCornerShape(24.dp))
}

@Composable
fun DrawerHeader() {
    Column(modifier = Modifier.padding(24.dp)) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp)) }
        Text("Audio Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun DrawerContent(pitch: Float, onPitchChange: (Float) -> Unit, speed: Float, onSpeedChange: (Float) -> Unit, onRequestPermission: (String) -> Unit, onShowGuide: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(24.dp)) {
        SettingSlider("Pitch", pitch, onPitchChange)
        Spacer(modifier = Modifier.height(20.dp))
        SettingSlider("Speed", speed, onSpeedChange)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Permissions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        
        PermissionItem("Mic Access", Manifest.permission.RECORD_AUDIO, Icons.Default.Mic, onRequestPermission)
        PermissionItem("Contacts Access", Manifest.permission.READ_CONTACTS, Icons.Default.Person, onRequestPermission)

        Spacer(modifier = Modifier.height(16.dp))
        NavigationDrawerItem(label = { Text("User Guide") }, selected = false, onClick = onShowGuide, icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) }, shape = RoundedCornerShape(12.dp))
    }
}

@Composable
fun PermissionItem(label: String, permission: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onRequest: (String) -> Unit) {
    val context = LocalContext.current
    val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    NavigationDrawerItem(
        label = { Text(label) }, 
        selected = false, 
        onClick = { onRequest(permission) }, 
        icon = { Icon(icon, null) }, 
        badge = { Text(if (isGranted) "OK" else "Fix") }, 
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun MainScreenContent(padding: PaddingValues, isReady: Boolean, isModelReady: Boolean, isTranslating: Boolean, isListening: Boolean, recognizedText: String, pickedContactName: String, currentLocale: Locale, supportedLocales: List<Locale>, downloadProgress: String?, onLanguageChange: (Locale) -> Unit, onSpeak: (String) -> Unit, onTranslateAndSpeak: (String) -> Unit, onSaveAudio: (String) -> Unit, onStartListening: () -> Unit, onStopListening: () -> Unit, onPickContact: () -> Unit, onRetryDownload: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var showLanguagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(recognizedText) { if (recognizedText.isNotBlank()) text = recognizedText }
    LaunchedEffect(pickedContactName) { if (pickedContactName.isNotBlank()) text = pickedContactName }

    if (showLanguagePicker) {
        LanguageSearchDialog(
            locales = supportedLocales,
            onDismiss = { showLanguagePicker = false },
            onSelect = { onLanguageChange(it); showLanguagePicker = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = { showLanguagePicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 2.dp) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, null)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target Language", style = MaterialTheme.typography.labelSmall)
                    Text(currentLocale.getDisplayName(Locale.ENGLISH), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.Search, null)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = text, 
                    onValueChange = { text = it }, 
                    placeholder = { Text("Enter text or pick a contact...") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    minLines = 4, 
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = onPickContact) { Icon(Icons.Default.Person, null) }
                            IconButton(onClick = { if (isListening) onStopListening() else onStartListening() }) {
                                Icon(if (isListening) Icons.Default.StopCircle else Icons.Default.Mic, null, tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSpeak(text) }, enabled = isReady && text.isNotBlank(), modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Speak") }
                    FilledTonalButton(onClick = { onTranslateAndSpeak(text) }, enabled = isReady && isModelReady && text.isNotBlank() && !isTranslating, modifier = Modifier.weight(1.2f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        if (isTranslating) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Text("Translate")
                    }
                    OutlinedButton(onClick = { onSaveAudio(text) }, enabled = isReady && (currentLocale == Locale.US || isModelReady) && text.isNotBlank(), modifier = Modifier.weight(0.8f).height(56.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Save, null) }
                }
            }
        }
        AnimatedVisibility(visible = isListening) {
            Column(modifier = Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Listening...", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        AnimatedVisibility(visible = !isReady || (!isModelReady && currentLocale != Locale.US)) {
            Column(modifier = Modifier.padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                val msg = if (!isReady) "Initializing TTS Engine..." else (downloadProgress ?: "Downloading ${currentLocale.displayLanguage}...")
                Text(msg, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                if (!isModelReady && currentLocale != Locale.US) {
                    TextButton(onClick = onRetryDownload, modifier = Modifier.padding(top = 8.dp)) { Text("Retry Connection") }
                }
            }
        }
    }
}

@Composable
fun LanguageSearchDialog(locales: List<Locale>, onDismiss: () -> Unit, onSelect: (Locale) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLocales = remember(searchQuery) { if (searchQuery.isEmpty()) locales else locales.filter { it.getDisplayName(Locale.ENGLISH).contains(searchQuery, ignoreCase = true) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxHeight(0.8f).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Language", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search 50+ languages...") }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) { items(filteredLocales) { locale -> ListItem(headlineContent = { Text(locale.getDisplayName(Locale.ENGLISH), fontWeight = FontWeight.Medium) }, supportingContent = { Text(locale.displayLanguage) }, modifier = Modifier.clickable { onSelect(locale) }) } }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun SettingSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.weight(1f)); Text("%.1f".format(value))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0.5f..2.0f)
    }
}
