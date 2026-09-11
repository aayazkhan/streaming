package com.streaming.platform.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.streaming.platform.android.ui.ProfileUiState
import com.streaming.platform.android.ui.ProfileViewModel
import com.streaming.platform.android.ui.components.ErrorMessage
import com.streaming.platform.android.ui.components.LoadingSpinner

@Composable
fun ProfileSelectScreen(viewModel: ProfileViewModel, onProfileSelected: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var newProfileName by remember { mutableStateOf("") }
    val isCreating by viewModel.isCreating.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(
            "Who's watching?",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        when (val state = uiState) {
            is ProfileUiState.Loading -> LoadingSpinner(modifier = Modifier.padding(top = 24.dp))
            is ProfileUiState.Error -> ErrorMessage(state.message, modifier = Modifier.padding(top = 24.dp))
            is ProfileUiState.Loaded -> Row(modifier = Modifier.padding(top = 24.dp)) {
                state.profiles.forEach { profile ->
                    Column(
                        modifier = Modifier.width(96.dp).padding(end = 12.dp)
                            .clickable {
                                viewModel.selectProfile(profile.id)
                                onProfileSelected()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            modifier = Modifier.size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(profile.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(profile.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        OutlinedTextField(
            value = newProfileName,
            onValueChange = { newProfileName = it },
            label = { Text("New profile name") },
            modifier = Modifier.padding(top = 24.dp),
        )
        Button(
            onClick = {
                viewModel.createProfile(newProfileName) { onProfileSelected() }
                newProfileName = ""
            },
            enabled = !isCreating && newProfileName.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(if (isCreating) "Adding..." else "Add profile")
        }
    }
}
