package com.example.pdfreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pdfreader.data.AppDatabase
import com.example.pdfreader.data.BooksRepository
import com.example.pdfreader.data.SettingsRepository
import com.example.pdfreader.ui.BookshelfScreen
import com.example.pdfreader.ui.ReaderScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val settings = remember { SettingsRepository(context) }
            val darkMode by settings.darkMode.collectAsState()
            MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()) {
                Surface {
                    val nav = rememberNavController()
                    val db = remember { AppDatabase.get(context) }
                    val repo = remember { BooksRepository(context, db.bookDao()) }
                    var importUri by remember { mutableStateOf<Uri?>(null) }
                    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            importUri = uri
                        }
                    }
                    LaunchedEffect(importUri) {
                        val u = importUri
                        if (u != null) {
                            val bookId = repo.addFromUri(u)
                            importUri = null
                            nav.navigate("reader/$bookId")
                        }
                    }
                    NavHost(navController = nav, startDestination = "books") {
                        composable("books") {
                            BookshelfScreen(
                                repo = repo,
                                settings = settings,
                                onImport = {
                                    openDoc.launch(arrayOf("application/pdf"))
                                },
                                onOpen = { id ->
                                    nav.navigate("reader/$id")
                                }
                            )
                        }
                        composable(
                            route = "reader/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getLong("id") ?: 0L
                            ReaderScreen(
                                repo = repo,
                                settings = settings,
                                bookId = id,
                                onBack = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
