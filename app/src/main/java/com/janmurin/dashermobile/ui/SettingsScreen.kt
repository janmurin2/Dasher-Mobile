package com.janmurin.dashermobile.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janmurin.dashermobile.DasherLanguage
import com.janmurin.dashermobile.DasherPrefs
import com.janmurin.dashermobile.InputMode
import com.janmurin.dashermobile.LanguageModel
import com.janmurin.dashermobile.R

val Inter = FontFamily(
    Font(R.font.inter_semibold_italic, FontWeight.SemiBold, FontStyle.Italic)
)

val InterItalic = FontFamily(
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(DasherPrefs.getLanguage(context)) }
    var selectedLanguageModel by remember { mutableStateOf(DasherPrefs.getLanguageModel(context)) }
    var selectedInputMode by remember { mutableStateOf(DasherPrefs.getInputMode(context)) }
    var selectedImeHeightPercent by remember { mutableStateOf(DasherPrefs.getImeHeightPercent(context)) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLanguageModelDialog by remember { mutableStateOf(false) }
    var showInputModeDialog by remember { mutableStateOf(false) }
    var showImeHeightDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedLanguage = DasherPrefs.getLanguage(context)
        selectedLanguageModel = DasherPrefs.getLanguageModel(context)
        selectedInputMode = DasherPrefs.getInputMode(context)
        selectedImeHeightPercent = DasherPrefs.getImeHeightPercent(context)
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings).lowercase(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = Inter,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.arrow_back_24px),
                            contentDescription = stringResource(id = R.string.icon_back),
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            SettingRow(
                iconResId = R.drawable.language_24px,
                iconContentDescription = stringResource(id = R.string.icon_language),
                title = stringResource(id = R.string.settings_language),
                currentValue = if (selectedLanguage == DasherLanguage.SLOVAK) "Slovak" else "English",
                onClick = { showLanguageDialog = true }
            )
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            SettingRow(
                iconResId = R.drawable.model_training_24px,
                iconContentDescription = stringResource(id = R.string.icon_language_model),
                title = stringResource(id = R.string.settings_language_model),
                currentValue = languageModelLabel(selectedLanguageModel),
                onClick = { showLanguageModelDialog = true }
            )
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            SettingRow(
                iconResId = R.drawable.touch_app_24px,
                iconContentDescription = stringResource(id = R.string.icon_input_mode),
                title = stringResource(id = R.string.settings_input_mode),
                currentValue = if (selectedInputMode == InputMode.TILT) "Tilt" else "Touch",
                onClick = { showInputModeDialog = true }
            )
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            SettingRow(
                iconResId = R.drawable.unfold_more_24px,
                iconContentDescription = stringResource(id = R.string.icon_ime_size),
                title = stringResource(id = R.string.settings_ime_size),
                currentValue = "$selectedImeHeightPercent%",
                onClick = { showImeHeightDialog = true }
            )
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = selectedLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { language ->
                DasherPrefs.setLanguage(context, language)
                selectedLanguage = language
                showLanguageDialog = false
            }
        )
    }

    if (showLanguageModelDialog) {
        LanguageModelDialog(
            currentModel = selectedLanguageModel,
            onDismiss = { showLanguageModelDialog = false },
            onSelect = { model ->
                DasherPrefs.setLanguageModel(context, model)
                selectedLanguageModel = model
                showLanguageModelDialog = false
            }
        )
    }

    if (showInputModeDialog) {
        InputModeDialog(
            currentMode = selectedInputMode,
            onDismiss = { showInputModeDialog = false },
            onSelect = { mode ->
                DasherPrefs.setInputMode(context, mode)
                selectedInputMode = mode
                showInputModeDialog = false
            }
        )
    }

    if (showImeHeightDialog) {
        ImeHeightDialog(
            currentPercent = selectedImeHeightPercent,
            onDismiss = { showImeHeightDialog = false },
            onSelect = { percent ->
                DasherPrefs.setImeHeightPercent(context, percent)
                selectedImeHeightPercent = percent
                showImeHeightDialog = false
            }
        )
    }
}

@Composable
private fun SettingRow(
    iconResId: Int,
    iconContentDescription: String,
    title: String,
    currentValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconResId),
            contentDescription = iconContentDescription,
            modifier = Modifier.size(24.dp),
            tint = Color.Black
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title.lowercase(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = Inter,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.Black
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = InterItalic,
                    fontStyle = FontStyle.Italic
                ),
                color = Color.Black
            )
        }
    }
}

@Composable
private fun LanguageDialog(
    currentLanguage: DasherLanguage,
    onDismiss: () -> Unit,
    onSelect: (DasherLanguage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.select_language)) },
        text = {
            Column {
                val languages = listOf(DasherLanguage.ENGLISH, DasherLanguage.SLOVAK)
                languages.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == language,
                            onClick = { onSelect(language) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (language == DasherLanguage.SLOVAK) "Slovak" else "English")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageModelDialog(
    currentModel: LanguageModel,
    onDismiss: () -> Unit,
    onSelect: (LanguageModel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.select_language_model)) },
        text = {
            Column {
                val models = listOf(LanguageModel.PPM, LanguageModel.WORD, LanguageModel.KENLM)
                models.forEach { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentModel == model,
                            onClick = { onSelect(model) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = languageModelLabel(model))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun languageModelLabel(model: LanguageModel): String {
    return when (model) {
        LanguageModel.PPM -> stringResource(id = R.string.language_model_ppm)
        LanguageModel.WORD -> stringResource(id = R.string.language_model_word)
        LanguageModel.KENLM -> stringResource(id = R.string.language_model_kenlm)
    }
}

@Composable
private fun InputModeDialog(
    currentMode: InputMode,
    onDismiss: () -> Unit,
    onSelect: (InputMode) -> Unit
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    val isTiltAvailable = tiltSensor != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.select_input_mode)) },
        text = {
            Column {
                val modes = listOf(InputMode.TOUCH, InputMode.TILT)
                modes.forEach { mode ->
                    val isEnabled = if (mode == InputMode.TILT) isTiltAvailable else true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { if (isEnabled) onSelect(mode) }
                            .padding(vertical = 8.dp)
                            .alpha(if (isEnabled) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { if (isEnabled) onSelect(mode) },
                            enabled = isEnabled
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val text = when (mode) {
                            InputMode.TOUCH -> stringResource(id = R.string.input_mode_touch)
                            InputMode.TILT -> if (isTiltAvailable) stringResource(id = R.string.input_mode_tilt) else stringResource(id = R.string.input_mode_tilt_unavailable)
                        }
                        Text(text = text)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun ImeHeightDialog(
    currentPercent: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.select_ime_size)) },
        text = {
            Column {
                val options = listOf(30, 40, 50, 60, 70)
                options.forEach { percent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(percent) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentPercent == percent,
                            onClick = { onSelect(percent) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "$percent%")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

