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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ShizukuShellExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var userService: IUserService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            userService = IUserService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    init {
        // Try to bind if already has permission
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
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell_executor")
        
        try {
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isServiceAlive(): Boolean {
        return try {
            userService?.asBinder()?.pingBinder() == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureService(forceRebind: Boolean = false): Boolean {
        if (!forceRebind && isServiceAlive()) return true
        if (!isShizukuAvailable()) return false

        if (forceRebind) {
            userService = null
        }
        bindService()

        // Wait up to 2 seconds for binding
        for (i in 1..20) {
            if (isServiceAlive()) return true
            kotlinx.coroutines.delay(100)
        }
        return false
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

    suspend fun executeForResult(command: String): ShellCommandResult = withContext(Dispatchers.IO) {
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

    suspend fun getClipboardText(): String? = withContext(Dispatchers.IO) {
        if (!ensureService()) return@withContext null
        try {
            userService?.getClipboardText()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
