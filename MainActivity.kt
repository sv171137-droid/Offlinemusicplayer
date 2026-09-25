package com.example.offlinemusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

data class Song(val id: Long, val title: String, val artist: String, val uri: String)

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicApp() }
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }

    private fun loadSongs(): List<Song> {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA
        )
        contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Unknown"
                val artist = c.getString(artistCol) ?: "Unknown artist"
                val uri = "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id"
                result += Song(id, title, artist, uri)
            }
        }
        return result
    }

    @Composable
    private fun MusicApp() {
        val context = this
        var permissionGranted by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= 33)
                    checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                else
                    checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            )
        }
        var songs by remember { mutableStateOf(emptyList<Song>()) }
        var query by remember { mutableStateOf("") }
        var current by remember { mutableStateOf<Song?>(null) }
        var isPlaying by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted
            if (granted) songs = loadSongs()
        }

        LaunchedEffect(permissionGranted) {
            if (permissionGranted) {
                songs = loadSongs()
                val token = SessionToken(context, ComponentName(context, MusicService::class.java))
                controller = MediaController.Builder(context, token).buildAsync().await()
            }
        }

        val filtered = songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true)
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFB9F6CA),
                background = Color(0xFF0B0B0F),
                surface = Color(0xFF15151B)
            )
        ) {
            Scaffold(
                containerColor = Color(0xFF0B0B0F),
                topBar = {
                    Column(Modifier.padding(20.dp, 18.dp, 20.dp, 8.dp)) {
                        Text("Offline Music", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${songs.size} songs on this device",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs or artists") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                },
                bottomBar = {
                    current?.let { song ->
                        Surface(tonalElevation = 8.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, maxLines = 1)
                                    Text(song.artist, color = Color.LightGray, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    controller?.let { c ->
                                        if (c.isPlaying) c.pause() else c.play()
                                        isPlaying = c.isPlaying
                                    }
                                }) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null
                                    )
                                }
                                IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                                    Icon(Icons.Default.SkipNext, null)
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                if (!permissionGranted) {
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Allow access to your music", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("This app only reads music stored on your device.")
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                if (Build.VERSION.SDK_INT >= 33)
                                    Manifest.permission.READ_MEDIA_AUDIO
                                else Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                        }) { Text("Allow music access") }
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text(if (songs.isEmpty()) "No music files found." else "No matching songs.")
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp)
                    ) {
                        items(filtered, key = { it.id }) { song ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        current = song
                                        controller?.apply {
                                            setMediaItems(songs.map { MediaItem.fromUri(it.uri) })
                                            val index = songs.indexOfFirst { it.id == song.id }
                                            seekToDefaultPosition(index)
                                            prepare()
                                            play()
                                            isPlaying = true
                                        }
                                    }
                                    .padding(vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, null)
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, maxLines = 1)
                                    Text(song.artist, color = Color.LightGray, maxLines = 1)
                                }
                                Icon(Icons.Default.PlayArrow, null)
                            }
                        }
                    }
                }
            }
        }
    }
}
