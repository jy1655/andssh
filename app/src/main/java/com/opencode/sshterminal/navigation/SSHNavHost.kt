package com.opencode.sshterminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.opencode.sshterminal.ui.connection.ConnectionListScreen
import com.opencode.sshterminal.ui.sftp.SftpBrowserScreen
import com.opencode.sshterminal.ui.terminal.TerminalScreen
import com.opencode.sshterminal.ui.terminal.TerminalViewModel

@Composable
fun SSHNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CONNECTION_LIST,
        modifier = modifier,
    ) {
        composable(Routes.CONNECTION_LIST) {
            ConnectionListScreen(
                onConnect = { connectionId ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("pendingConnectionId", connectionId)
                    navController.navigate(Routes.TERMINAL) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = Routes.TERMINAL) {
            val viewModel: TerminalViewModel = hiltViewModel()

            LaunchedEffect(Unit) {
                val pendingId =
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("pendingConnectionId")
                if (pendingId != null) {
                    viewModel.openTab(pendingId)
                }
            }

            TerminalScreen(
                onNavigateToSftp = { connectionId ->
                    navController.navigate(Routes.sftp(connectionId))
                },
                onAllTabsClosed = {
                    navController.popBackStack(Routes.CONNECTION_LIST, inclusive = false)
                },
                viewModel = viewModel,
            )
        }

        composable(
            route = Routes.SFTP,
            arguments = listOf(navArgument("connectionId") { type = NavType.StringType }),
        ) {
            SftpBrowserScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
