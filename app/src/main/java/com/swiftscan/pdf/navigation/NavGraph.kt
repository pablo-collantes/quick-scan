package com.swiftscan.pdf.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.swiftscan.pdf.ui.home.HomeScreen
import com.swiftscan.pdf.ui.merge.MergeScreen
import com.swiftscan.pdf.ui.review.ReviewScreen
import com.swiftscan.pdf.ui.scanner.ScannerScreen

sealed class Route(val route: String) {
    data object Home : Route("home")
    data object Scanner : Route("scanner")
    data object Review : Route("review")
    data object Merge : Route("merge")
}

@Composable
fun SwiftScanNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home.route,
    ) {
        composable(Route.Home.route) {
            HomeScreen(
                onNavigateToScanner = { navController.navigate(Route.Scanner.route) },
                onNavigateToMerge = { navController.navigate(Route.Merge.route) },
            )
        }

        composable(Route.Scanner.route) {
            ScannerScreen(
                onNavigateToReview = {
                    navController.navigate(Route.Review.route) {
                        popUpTo(Route.Scanner.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Route.Review.route) {
            ReviewScreen(
                onNavigateToScanner = {
                    navController.navigate(Route.Scanner.route)
                },
                onNavigateHome = {
                    navController.navigate(Route.Home.route) {
                        popUpTo(Route.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.Merge.route) {
            MergeScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
