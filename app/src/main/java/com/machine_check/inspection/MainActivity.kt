package com.machine_check.inspection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.machine_check.inspection.ui.inspection.InspectionScreen
import com.machine_check.inspection.ui.scan.ScanScreen
import com.machine_check.inspection.ui.theme.MachineCheckTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MachineCheckTheme {
                InspectionNavHost()
            }
        }
    }
}

@Composable
fun InspectionNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "scan"
    ) {
        // 扫码页面
        composable("scan") {
            ScanScreen(
                onNavigateToInspection = { deviceModel, employeeId ->
                    val encodedModel = URLEncoder.encode(deviceModel, "UTF-8")
                    val encodedEmployee = URLEncoder.encode(employeeId, "UTF-8")
                    navController.navigate("inspection/$encodedModel/$encodedEmployee")
                }
            )
        }

        // 点检页面
        composable(
            route = "inspection/{deviceModel}/{employeeId}",
            arguments = listOf(
                navArgument("deviceModel") { type = NavType.StringType },
                navArgument("employeeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val deviceModel = URLDecoder.decode(
                backStackEntry.arguments?.getString("deviceModel") ?: "", "UTF-8"
            )
            val employeeId = URLDecoder.decode(
                backStackEntry.arguments?.getString("employeeId") ?: "", "UTF-8"
            )
            InspectionScreen(
                deviceModel = deviceModel,
                employeeId = employeeId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

