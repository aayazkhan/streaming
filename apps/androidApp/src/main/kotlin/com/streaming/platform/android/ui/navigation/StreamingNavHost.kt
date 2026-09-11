package com.streaming.platform.android.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.streaming.platform.android.StreamingApplication
import com.streaming.platform.authentication.AuthState
import com.streaming.platform.android.ui.AuthViewModel
import com.streaming.platform.android.ui.BrowseViewModel
import com.streaming.platform.android.ui.ContentDetailViewModelFactory
import com.streaming.platform.android.ui.AppViewModelFactory
import com.streaming.platform.android.ui.PlayerViewModelFactory
import com.streaming.platform.android.ui.ProfileViewModel
import com.streaming.platform.android.ui.SearchViewModel
import com.streaming.platform.android.ui.WatchlistViewModel
import com.streaming.platform.android.ui.HomeViewModel
import com.streaming.platform.android.ui.screens.BrowseScreen
import com.streaming.platform.android.ui.screens.ContentDetailScreen
import com.streaming.platform.android.ui.screens.HomeScreen
import com.streaming.platform.android.ui.screens.LoginScreen
import com.streaming.platform.android.ui.screens.PlayerScreen
import com.streaming.platform.android.ui.screens.ProfileSelectScreen
import com.streaming.platform.android.ui.screens.RegisterScreen
import com.streaming.platform.android.ui.screens.SearchScreen
import com.streaming.platform.android.ui.screens.WatchlistScreen
import com.streaming.platform.android.ui.components.LoadingSpinner

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PROFILES = "profiles"
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val WATCHLIST = "watchlist"
    const val CONTENT_DETAIL = "content/{contentId}"
    const val PLAYER = "player/{contentId}"
    fun contentDetail(id: String) = "content/$id"
    fun player(id: String) = "player/$id"
}

@Composable
fun StreamingNavHost() {
    val context = LocalContext.current
    val container = (context.applicationContext as StreamingApplication).container
    val factory = remember(container) { AppViewModelFactory(container) }
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.SignedOut) {
            container.activeProfileHolder.set(null)
        }
    }

    // NavHost's startDestination is read once, at first composition — restoreSession() resolves
    // asynchronously, so without this gate every cold start with a valid stored session briefly
    // (but permanently, since startDestination never re-evaluates) fell back to the Login screen.
    if (authState is AuthState.Initializing) {
        LoadingSpinner()
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(authState, currentRoute) {
        // A mid-session sign-out (refresh token itself rejected, not just an expired access token —
        // ApiClient already retries those transparently) needs an explicit route back to Login:
        // NavHost's startDestination doesn't react to state changes after first composition, so
        // without this the screen the user was on would just sit there showing a raw error.
        if (authState is AuthState.SignedOut && currentRoute != null &&
            currentRoute != Routes.LOGIN && currentRoute != Routes.REGISTER
        ) {
            navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        }
    }
    val showChrome = authState is AuthState.SignedIn && currentRoute != Routes.LOGIN && currentRoute != Routes.REGISTER &&
        currentRoute != Routes.PROFILES && currentRoute != Routes.PLAYER

    Scaffold(
        topBar = {
            if (showChrome) {
                Row(
                    // enableEdgeToEdge() (MainActivity) draws content behind the status bar —
                    // without this inset padding, the title and status bar icons visually overlap.
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Streaming", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Row {
                        IconButton(onClick = { navController.navigate(Routes.HOME) }) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                        IconButton(onClick = { navController.navigate(Routes.BROWSE) }) {
                            Icon(Icons.Default.ViewList, contentDescription = "Browse")
                        }
                        IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { navController.navigate(Routes.WATCHLIST) }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "My List")
                        }
                        TextButton(onClick = { authViewModel.logout() }) { Text("Sign out") }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (authState is AuthState.SignedIn) Routes.PROFILES else Routes.LOGIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(authViewModel, onNavigateToRegister = { navController.navigate(Routes.REGISTER) })
            }
            composable(Routes.REGISTER) {
                RegisterScreen(authViewModel, onNavigateToLogin = { navController.navigate(Routes.LOGIN) })
            }
            composable(Routes.PROFILES) {
                val viewModel: ProfileViewModel = viewModel(factory = factory)
                ProfileSelectScreen(viewModel, onProfileSelected = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.PROFILES) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(factory = factory)
                HomeScreen(viewModel, onContentClick = { navController.navigate(Routes.contentDetail(it)) })
            }
            composable(Routes.BROWSE) {
                val viewModel: BrowseViewModel = viewModel(factory = factory)
                BrowseScreen(viewModel, onContentClick = { navController.navigate(Routes.contentDetail(it)) })
            }
            composable(Routes.SEARCH) {
                val viewModel: SearchViewModel = viewModel(factory = factory)
                SearchScreen(viewModel, onContentClick = { navController.navigate(Routes.contentDetail(it)) })
            }
            composable(Routes.WATCHLIST) {
                val viewModel: WatchlistViewModel = viewModel(factory = factory)
                WatchlistScreen(viewModel, onContentClick = { navController.navigate(Routes.contentDetail(it)) })
            }
            composable(Routes.CONTENT_DETAIL) { backStackEntry ->
                val contentId = backStackEntry.arguments?.getString("contentId") ?: return@composable
                val viewModel: com.streaming.platform.android.ui.ContentDetailViewModel = viewModel(
                    factory = ContentDetailViewModelFactory(container, contentId),
                )
                ContentDetailScreen(viewModel, onPlayClick = { navController.navigate(Routes.player(it)) })
            }
            composable(Routes.PLAYER) { backStackEntry ->
                val contentId = backStackEntry.arguments?.getString("contentId") ?: return@composable
                val viewModel: com.streaming.platform.android.ui.PlayerViewModel = viewModel(
                    factory = PlayerViewModelFactory(container, contentId),
                )
                PlayerScreen(viewModel)
            }
        }
    }
}
