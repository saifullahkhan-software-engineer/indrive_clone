package com.example.indriveclone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.ui.screens.DriverHomeScreen
import com.example.indriveclone.ui.screens.DriverOnTheWayScreen
import com.example.indriveclone.ui.screens.DriverRequestDetailScreen
import com.example.indriveclone.ui.screens.OffersScreen
import com.example.indriveclone.ui.screens.RiderMapScreen
import com.example.indriveclone.ui.screens.RoleSelectionScreen
import com.example.indriveclone.ui.screens.SettingsScreen
import com.example.indriveclone.viewmodel.AppViewModel

object Routes {
    const val ROLE = "role"
    const val RIDER = "rider"
    const val OFFERS = "rider/offers/{rideId}"
    const val DRIVER_ON_THE_WAY = "rider/ontheway/{rideId}"
    const val DRIVER_HOME = "driver"
    const val DRIVER_REQUEST = "driver/request/{rideId}"
    const val SETTINGS = "settings"

    fun offers(rideId: String) = "rider/offers/$rideId"
    fun driverOnTheWay(rideId: String) = "rider/ontheway/$rideId"
    fun driverRequest(rideId: String) = "driver/request/$rideId"
}

/**
 * Single navigation graph. The rider and driver flows share the repository, so the role switch in the
 * overflow menu simply swaps the root destination.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory),
) {
    val role by appViewModel.role.collectAsStateWithLifecycle()

    val switchRole: () -> Unit = {
        val target = if (role == UserRole.DRIVER) Routes.RIDER else Routes.DRIVER_HOME
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    val openSettings: () -> Unit = { navController.navigate(Routes.SETTINGS) }
    val resetDemoData: () -> Unit = {
        appViewModel.resetDemoData()
        navController.navigate(if (role == UserRole.DRIVER) Routes.DRIVER_HOME else Routes.RIDER) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Routes.ROLE) {
        composable(Routes.ROLE) {
            RoleSelectionScreen(
                onRoleSelected = { selected ->
                    appViewModel.selectRole(selected)
                    navController.navigate(
                        if (selected == UserRole.RIDER) Routes.RIDER else Routes.DRIVER_HOME,
                    ) {
                        popUpTo(Routes.ROLE) { inclusive = true }
                    }
                },
                onOpenSettings = openSettings,
            )
        }

        composable(Routes.RIDER) {
            RiderMapScreen(
                role = role,
                onOpenSettings = openSettings,
                onSwitchRole = switchRole,
                onResetDemoData = resetDemoData,
                onOpenOffers = { rideId -> navController.navigate(Routes.offers(rideId)) },
            )
        }

        composable(
            route = Routes.OFFERS,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { entry ->
            val rideId = entry.arguments?.getString("rideId").orEmpty()
            OffersScreen(
                rideId = rideId,
                role = role,
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
                onSwitchRole = switchRole,
                onResetDemoData = resetDemoData,
                onDriverOnTheWay = { acceptedRideId ->
                    navController.navigate(Routes.driverOnTheWay(acceptedRideId)) {
                        // The offers list is done once a driver is accepted.
                        popUpTo(Routes.OFFERS) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.DRIVER_ON_THE_WAY,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { entry ->
            val rideId = entry.arguments?.getString("rideId").orEmpty()
            DriverOnTheWayScreen(
                rideId = rideId,
                role = role,
                onBack = { navController.popBackStack(Routes.RIDER, inclusive = false) },
                onOpenSettings = openSettings,
                onSwitchRole = switchRole,
                onResetDemoData = resetDemoData,
                onTripCancelled = {
                    navController.navigate(Routes.RIDER) {
                        popUpTo(Routes.RIDER) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.DRIVER_HOME) {
            DriverHomeScreen(
                role = role,
                onOpenSettings = openSettings,
                onSwitchRole = switchRole,
                onResetDemoData = resetDemoData,
                onOpenRequest = { rideId -> navController.navigate(Routes.driverRequest(rideId)) },
            )
        }

        composable(
            route = Routes.DRIVER_REQUEST,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { entry ->
            val rideId = entry.arguments?.getString("rideId").orEmpty()
            DriverRequestDetailScreen(
                rideId = rideId,
                role = role,
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
                onSwitchRole = switchRole,
                onResetDemoData = resetDemoData,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                role = role,
                onBack = { navController.popBackStack() },
                onSwitchRole = switchRole,
                onResetDemoData = resetDemoData,
            )
        }
    }
}
