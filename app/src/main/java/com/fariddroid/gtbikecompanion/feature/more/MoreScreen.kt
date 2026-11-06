package com.fariddroid.gtbikecompanion.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fariddroid.gtbikecompanion.BuildConfig
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.demo.DemoScenario
import com.fariddroid.gtbikecompanion.core.demo.DemoTelemetryRepository
import com.fariddroid.gtbikecompanion.data.AppLanguage
import com.fariddroid.gtbikecompanion.data.AppSettings
import com.fariddroid.gtbikecompanion.data.LocaleController
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.ui.components.RadioGroup
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.format.Labels
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import com.fariddroid.gtbikecompanion.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Debug-only scenario switcher. Release builds never show it. */
@HiltViewModel
class DemoControlViewModel @Inject constructor(private val demo: DemoTelemetryRepository) : ViewModel() {
    val scenario = demo.scenario
    fun select(s: DemoScenario) = demo.select(s)
}

@Composable
fun MoreScreen(
    settings: AppSettings,
    onTheme: (ThemeMode) -> Unit,
    onUnits: (UnitSystem) -> Unit,
    onOpenHelpTopics: () -> Unit,
    onOpenConnection: () -> Unit = {},
    onOpenPcSettings: () -> Unit = {},
    onOpenGameOptions: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenActivities: () -> Unit = {},
    onOpenRiders: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    demoControl: DemoControlViewModel?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("moreScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.more_title), style = MaterialTheme.typography.headlineSmall)

        SectionCard(stringResource(R.string.more_section_pc), help = HelpTopic.CONNECTION_STATUS) {
            LinkRow(stringResource(R.string.more_pc_settings), onOpenPcSettings, "openPcSettings")
            LinkRow(stringResource(R.string.more_game_options), onOpenGameOptions, "openGameOptions")
            LinkRow(stringResource(R.string.more_devices), onOpenDevices, "openDevices")
            LinkRow(stringResource(R.string.more_activities), onOpenActivities, "openActivities")
            LinkRow(stringResource(R.string.more_riders), onOpenRiders, "openRiders")
            LinkRow(stringResource(R.string.more_connection), onOpenConnection, "openConnection")
        }

        SectionCard(stringResource(R.string.more_language), help = HelpTopic.APP_LANGUAGE) {
            RadioGroup(
                options = listOf(
                    AppLanguage.ENGLISH to stringResource(R.string.language_english),
                    AppLanguage.SPANISH to stringResource(R.string.language_spanish),
                ),
                selected = LocaleController.current(),
                onSelect = LocaleController::set,
            )
        }
        SectionCard(stringResource(R.string.more_theme), help = HelpTopic.APP_THEME) {
            RadioGroup(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ),
                selected = settings.theme,
                onSelect = onTheme,
            )
        }
        SectionCard(stringResource(R.string.more_units), help = HelpTopic.APP_UNITS) {
            RadioGroup(
                options = listOf(
                    UnitSystem.METRIC to stringResource(R.string.units_metric),
                    UnitSystem.IMPERIAL to stringResource(R.string.units_imperial),
                ),
                selected = settings.units,
                onSelect = onUnits,
            )
        }
        SectionCard(stringResource(R.string.more_section_help), help = HelpTopic.DIAGNOSTICS_LOCAL) {
            LinkRow(stringResource(R.string.more_help_topics), onOpenHelpTopics, "openHelpTopics")
            LinkRow(stringResource(R.string.diagnostics_title), onOpenDiagnostics, "openDiagnostics")
        }
        SectionCard(stringResource(R.string.more_about)) {
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.welcome_unofficial),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (BuildConfig.DEBUG && demoControl != null) {
            val scenario by demoControl.scenario.collectAsStateWithLifecycle()
            SectionCard(stringResource(R.string.more_section_developer)) {
                Text(stringResource(R.string.more_demo_scenario), style = MaterialTheme.typography.bodyMedium)
                RadioGroup(
                    options = DemoScenario.entries.map { it to stringResource(Labels.scenario(it)) },
                    selected = scenario,
                    onSelect = demoControl::select,
                )
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Every local help topic, readable offline. Tapping opens the same sheet as the inline «?». */
@Composable
fun HelpTopicsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("helpTopicsScreen"),
    ) {
        Text(stringResource(R.string.more_help_topics), style = MaterialTheme.typography.headlineSmall)
        HelpTopic.entries.forEach { topic ->
            val title = stringResource(topic.title)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                OptionHelpButton(topic, title)
            }
        }
    }
}
