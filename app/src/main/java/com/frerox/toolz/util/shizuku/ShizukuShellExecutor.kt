/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.util.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.frerox.toolz.ICommandCallback
import com.frerox.toolz.IUserService
import com.frerox.toolz.util.shizuku.ShizukuUserService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ShizukuShellExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile private var userService: IUserService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            userService = if (binder == null) null else IUserService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shell_executor")
    }

    /** Serializes bind attempts so two captures cannot spawn two user services. */
    private val bindMutex = Mutex()

    /**
     * Clipboard reads run on a dedicated daemon thread so a wedged system
     * binder can never block the foreground watcher (or its capture mutex)
     * forever. A timed-out read is abandoned and the caller proceeds.
     */
    private val clipboardReadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "clipboard-shizuku-read").apply { isDaemon = true }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
    }

    init {
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        // Try to bind if already has permission.
        if (isShizukuAvailable()) {
            bindService()
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && 
                    Shizuku.getVersion() >= 11 &&
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun bindService() {
        if (isServiceAlive()) return
        userService = null
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Actually unbinds the Shizuku user service. The old implementation only
     * dropped the local reference, leaking the remote process/binding.
     */
    fun unbindService() {
        userService = null
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: Exception) {
        }
    }

    private fun isServiceAlive(): Boolean {
        return try {
            userService?.asBinder()?.pingBinder() == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ensures the shell user service is bound. Binding is serialized: two
     * concurrent clipboard reads used to call `bindUserService` twice and leak
     * one of the returned service connections.
     */
    suspend fun ensureService(forceRebind: Boolean = false): Boolean = bindMutex.withLock {
        if (!forceRebind && isServiceAlive()) return@withLock true
        if (!isShizukuAvailable()) return@withLock false

        if (forceRebind) {
            unbindService()
        }
        bindService()

        // Binding is asynchronous. Wait up to 3 seconds for the service.
        repeat(30) {
            if (isServiceAlive()) return@withLock true
            delay(100)
        }
        isServiceAlive()
    }

    fun execute(command: String): Flow<ShellOutput> = callbackFlow {
        if (!ensureService()) {
            trySend(ShellOutput.Error("Shizuku service not available"))
            close()
            return@callbackFlow
        }
        val service = userService
        if (service == null) {
            trySend(ShellOutput.Error("Shizuku service bound state is invalid"))
            close()
            return@callbackFlow
        }

        val callback = object : ICommandCallback.Stub() {
            override fun onOutput(line: String) {
                trySend(ShellOutput.StdOut(line))
            }

            override fun onError(line: String) {
                trySend(ShellOutput.StdErr(line))
            }

            override fun onExit(code: Int) {
                trySend(ShellOutput.Exit(code))
                close()
            }
        }

        try {
            service.runCommandWithCallback(command, callback)
        } catch (e: Exception) {
            trySend(ShellOutput.Error("Remote error: ${e.message}"))
            close()
        }

        awaitClose {
            // We can't really cancel the remote process easily without more AIDL methods
        }
    }.flowOn(Dispatchers.IO)

    suspend fun executeSingle(command: String): String = withContext(Dispatchers.IO) {
        executeForResult(command).combinedOutput
    }

    /**
     * Runs [command] with a hard client-side timeout. On timeout the returned result has exitCode -1;
     * note the remote process may still finish server-side (no cancel AIDL), but callers always proceed.
     */
    suspend fun executeForResult(command: String, timeoutMs: Long = 8_000): ShellCommandResult =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) { executeForResultInternal(command) }
                ?: ShellCommandResult(
                    command = command,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Timed out after ${timeoutMs}ms"
                )
        }

    private suspend fun executeForResultInternal(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
        if (!ensureService()) {
            return@withContext ShellCommandResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku service not available"
            )
        }

        suspendCancellableCoroutine { continuation ->
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val service = userService
            if (service == null) {
                continuation.resume(
                    ShellCommandResult(
                        command = command,
                        exitCode = -1,
                        stdout = "",
                        stderr = "Shizuku service bound state is invalid"
                    )
                )
                return@suspendCancellableCoroutine
            }
            val callback = object : ICommandCallback.Stub() {
                override fun onOutput(line: String) {
                    stdout.append(line).append('\n')
                }

                override fun onError(line: String) {
                    stderr.append(line).append('\n')
                }

                override fun onExit(code: Int) {
                    if (continuation.isActive) {
                        continuation.resume(
                            ShellCommandResult(
                                command = command,
                                exitCode = code,
                                stdout = stdout.toString().trim(),
                                stderr = stderr.toString().trim()
                            )
                        )
                    }
                }
            }
            try {
                service.runCommandWithCallback(command, callback)
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    /**
     * Reads the clipboard through the shell user service with a hard timeout.
     * The remote binder call is not cancellable, so it runs on a dedicated
     * daemon thread; a wedged read is abandoned instead of stalling the
     * foreground watcher forever.
     */
    suspend fun getClipboardText(timeoutMs: Long = 4_000): String? = withContext(Dispatchers.IO) {
        if (!ensureService()) return@withContext null
        val service = userService ?: return@withContext null
        try {
            val future = clipboardReadExecutor.submit<String?> { service.clipboardText }
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            android.util.Log.w("ShizukuShellExecutor", "Clipboard read timed out after ${timeoutMs}ms")
            null
        } catch (e: Exception) {
            android.util.Log.w("ShizukuShellExecutor", "Clipboard read failed: ${e.message}")
            null
        }
    }

    /**
     * Grants Toolz the `android:read_clipboard` app-op through the Shizuku shell.
     * This is the fallback for ROMs where `cmd clipboard get` is unavailable.
     */
    suspend fun grantClipboardAppOp(): ShellCommandResult = executeForResult(
        "appops set ${context.packageName} READ_CLIPBOARD allow",
    )

    suspend fun revokeClipboardAppOp(): ShellCommandResult = executeForResult(
        "appops set ${context.packageName} READ_CLIPBOARD default",
    )

    /** Shell-side verification (the app-side check lives in ClipboardGate). */
    suspend fun isClipboardAppOpAllowed(): Boolean {
        val result = executeForResult("appops get ${context.packageName} READ_CLIPBOARD")
        if (!result.isSuccess) return false
        val output = result.stdout.lowercase()
        return output.contains("allow") && !output.contains("ignore") && !output.contains("deny")
    }
}

sealed class ShellOutput {
    data class StdOut(val line: String) : ShellOutput()
    data class StdErr(val line: String) : ShellOutput()
    data class Error(val message: String) : ShellOutput()
    data class Exit(val code: Int) : ShellOutput()
}

data class ShellCommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0

    val combinedOutput: String
        get() = listOf(stdout, stderr)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { if (isSuccess) "" else "Exit code $exitCode" }
}
