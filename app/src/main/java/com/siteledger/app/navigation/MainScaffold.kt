package com.siteledger.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.siteledger.app.di.AppContainer
import com.siteledger.app.feature.dashboard.DashboardScreen
import com.siteledger.app.feature.expenses.ExpenseFormScreen
import com.siteledger.app.feature.expenses.ExpensesScreen
import com.siteledger.app.feature.projects.ProjectDetailScreen
import com.siteledger.app.feature.projects.ProjectFormScreen
import com.siteledger.app.feature.projects.ProjectListScreen
import com.siteledger.app.feature.reports.ReportsScreen
import com.siteledger.app.feature.settings.CategoriesScreen
import com.siteledger.app.feature.settings.SettingsScreen
import com.siteledger.app.feature.suppliers.SupplierFormScreen
import com.siteledger.app.feature.suppliers.SupplierListScreen
import com.siteledger.app.feature.sync.SyncScreen
import kotlinx.coroutines.launch

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    TopLevelDestination(Routes.PROJECTS, "Projects", Icons.Filled.Place),
    TopLevelDestination(Routes.EXPENSES, "Expenses", Icons.Filled.ShoppingCart),
    TopLevelDestination(Routes.REPORTS, "Reports", Icons.Filled.DateRange),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
fun MainScaffold(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.TOP_LEVEL

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigateToTab(navController, destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        val bottomPadding = innerPadding.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
                .consumeWindowInsets(PaddingValues(bottom = bottomPadding)),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    container = container,
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                    onAddExpense = { navController.navigate(Routes.expenseForm()) },
                    onAddProject = { navController.navigate(Routes.projectForm()) },
                    onOpenProjects = { navigateToTab(navController, Routes.PROJECTS) },
                    onOpenExpense = { navController.navigate(Routes.expenseForm(expenseId = it)) },
                    onOpenProject = { navController.navigate(Routes.projectDetail(it)) },
                )
            }

            composable(Routes.PROJECTS) {
                ProjectListScreen(
                    container = container,
                    onAddProject = { navController.navigate(Routes.projectForm()) },
                    onOpenProject = { navController.navigate(Routes.projectDetail(it)) },
                )
            }

            composable(
                route = Routes.PROJECT_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.StringType }),
            ) { entry ->
                val projectId = entry.arguments?.getString(Routes.ARG_PROJECT_ID).orEmpty()
                ProjectDetailScreen(
                    container = container,
                    projectId = projectId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.projectForm(it)) },
                    onAddExpense = { navController.navigate(Routes.expenseForm(projectId = it)) },
                    onOpenExpense = { navController.navigate(Routes.expenseForm(expenseId = it)) },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PROJECT_FORM,
                arguments = listOf(
                    navArgument(Routes.ARG_PROJECT_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val projectId = entry.arguments?.getString(Routes.ARG_PROJECT_ID)?.ifBlank { null }
                ProjectFormScreen(
                    container = container,
                    projectId = projectId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }

            composable(Routes.EXPENSES) {
                ExpensesScreen(
                    container = container,
                    onAddExpense = { navController.navigate(Routes.expenseForm()) },
                    onOpenExpense = { navController.navigate(Routes.expenseForm(expenseId = it)) },
                )
            }

            composable(
                route = Routes.EXPENSE_FORM,
                arguments = listOf(
                    navArgument(Routes.ARG_EXPENSE_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.ARG_PROJECT_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val expenseId = entry.arguments?.getString(Routes.ARG_EXPENSE_ID)?.ifBlank { null }
                val projectId = entry.arguments?.getString(Routes.ARG_PROJECT_ID)?.ifBlank { null }
                ExpenseFormScreen(
                    container = container,
                    expenseId = expenseId,
                    initialProjectId = projectId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(Routes.SUPPLIERS) {
                SupplierListScreen(
                    container = container,
                    onAddSupplier = { navController.navigate(Routes.supplierForm()) },
                    onOpenSupplier = { navController.navigate(Routes.supplierForm(it)) },
                )
            }

            composable(
                route = Routes.SUPPLIER_FORM,
                arguments = listOf(
                    navArgument(Routes.ARG_SUPPLIER_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val supplierId = entry.arguments?.getString(Routes.ARG_SUPPLIER_ID)?.ifBlank { null }
                SupplierFormScreen(
                    container = container,
                    supplierId = supplierId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(Routes.REPORTS) {
                ReportsScreen(container = container)
            }

            composable(Routes.SYNC) {
                val scope = rememberCoroutineScope()
                SyncScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onSignIn = {
                        scope.launch { container.authRepository.signOut(keepLocalData = true) }
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenSuppliers = { navController.navigate(Routes.SUPPLIERS) },
                    onOpenSync = { navController.navigate(Routes.SYNC) },
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
