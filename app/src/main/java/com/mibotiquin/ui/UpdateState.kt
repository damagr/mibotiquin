package com.mibotiquin.ui

import com.mibotiquin.data.api.GitHubRelease
import java.io.File

sealed class UpdateState {
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val release: GitHubRelease) : UpdateState()
    object Preparing : UpdateState()   // creando backup antes de descargar
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}